package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.dataOrNull
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Runs after SyncQuizResultsWorker successfully grades a batch of offline
 * answers. For every attempt that was left provisional (see QuizViewModel /
 * QuizResult.provisional), checks whether every answer in it now has a
 * server verdict; if so, asks the server to finalize the attempt — which
 * recomputes the score from the verified quizResults and issues the
 * certificate + CyberDefender badge — then awards XP and marks the quiz
 * completed locally and flips the attempt to final.
 *
 * The certificate and badge are issued *only* by the server (the
 * finalizeQuizAttempt callable); this use case never writes them, so they
 * cannot be forged by a malicious client. The callable is idempotent, so a
 * retried pass never double-issues them.
 *
 * This deliberately mirrors QuizViewModel.finishQuiz's reward logic rather
 * than sharing code with it: that method runs at quiz-completion time with
 * a live Question list in memory, while this runs later, in the background,
 * with only what's persisted in Room. Keeping them separate avoids forcing
 * QuizViewModel's UI-facing flow to route through a background-safe
 * abstraction it doesn't otherwise need.
 */
class FinalizeQuizAttemptsUseCase
    @Inject
    constructor(
        private val quizRepository: QuizRepository,
        private val awardXp: AwardXpUseCase,
        private val userRepository: UserRepository,
    ) {
        suspend operator fun invoke() {
            val readyAttempts = quizRepository.getAttemptsReadyToFinalize()

            for (attempt in readyAttempts) {
                try {
                    processAttempt(attempt)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Don't let one bad attempt take down the rest of this batch —
                    // it simply stays provisional and gets retried next pass.
                }
            }
        }

        private suspend fun processAttempt(attempt: ReadyToFinalizeAttempt) {
            // Server recomputes the score from the verified quizResults and
            // issues the certificate + CyberDefender badge. Idempotent, so a
            // retried pass never double-issues. If it fails, leave the attempt
            // provisional and retry later — do NOT award XP yet, because addXp
            // is an increment that is not itself idempotent.
            val finalize = quizRepository.finalizeQuizAttemptServer(attempt.resultId)
            val fr = finalize.dataOrNull ?: return

            val xpResult =
                awardXp(
                    userId = attempt.userId,
                    correctCount = fr.correctCount,
                    totalCount = attempt.totalQuestions,
                )
            val xpEarned = xpResult.dataOrNull ?: return

            // XP has now landed remotely. Retrying awardXp on a future pass would
            // double-increment it, so from this point on we must finalize this
            // attempt no matter what happens below — otherwise it would stay
            // provisional forever and get double-awarded XP next sync.
            userRepository.markQuizCompleted(attempt.userId, attempt.quizId)

            // The certificate/badge were already issued by the server above; we
            // only record the (server-derived) outcome locally for display and
            // to stop this attempt from being reprocessed.
            quizRepository.finalizeAttempt(
                resultId = attempt.resultId,
                score = fr.score,
                correctCount = fr.correctCount,
                percentage = fr.percentage,
                xpEarned = xpEarned,
                passed = fr.passed,
            )
        }
    }
