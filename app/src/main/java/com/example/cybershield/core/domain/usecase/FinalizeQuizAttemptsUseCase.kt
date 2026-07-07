package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.QuizScoring
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.dataOrNull
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Runs after SyncQuizResultsWorker successfully grades a batch of offline
 * answers. For every attempt that was left provisional (see QuizViewModel /
 * QuizResult.provisional), checks whether every answer in it now has a
 * server verdict; if so, awards XP/badge/certificate off the *recomputed,
 * fully-verified* score — never the optimistic in-session numbers — and
 * flips the attempt to final.
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
        private val generateCertificate: GenerateCertificateUseCase,
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
            val percentage =
                if (attempt.totalQuestions > 0) (attempt.correctCount * 100) / attempt.totalQuestions else 0
            val passed = percentage >= QuizScoring.PASS_PERCENTAGE

            val xpResult = awardXp(userId = attempt.userId, correctCount = attempt.correctCount, totalCount = attempt.totalQuestions)
            val xpEarned = xpResult.dataOrNull

            if (xpEarned == null) {
                // awardXp failed outright — nothing was written (addXp uses
                // FieldValue.increment, so a failed call never partially lands),
                // so it's safe to leave this attempt provisional and let the
                // next sync pass (or the periodic safety-net worker) retry it
                // from scratch. Finalizing here with a fabricated xpEarned = 0
                // would silently and *permanently* under-reward the user, since
                // finalizeAttempt() flips provisional to false and this attempt
                // is never looked at again.
                return
            }

            // XP has now landed remotely. Retrying awardXp on a future pass would
            // double-increment it, so from this point on we must finalize this
            // attempt no matter what happens below — otherwise it would stay
            // provisional forever and get double-awarded XP next sync.
            userRepository.markQuizCompleted(attempt.userId, attempt.quizId)

            if (passed) {
                userRepository.awardBadge(attempt.userId, "CyberDefender")
                val displayName =
                    (userRepository.getUserProfileOnce(attempt.userId) as? Result.Success)?.data?.displayName
                        ?: "CyberShield User"
                generateCertificate(
                    userId = attempt.userId,
                    userName = displayName,
                    moduleId = attempt.moduleId,
                    moduleName = attempt.moduleName,
                    quizTitle = attempt.quizTitle,
                    score = attempt.score,
                )
                // markQuizCompleted/awardBadge/certificate failures here have no
                // UI to report to — there's no active screen watching this
                // attempt anymore. They're simply left off if they fail; the
                // person can notice and regenerate/retry from their profile.
                // Only the XP number above is guaranteed reliable, since it's
                // the one thing that can't be safely retried after this point.
            }

            quizRepository.finalizeAttempt(
                resultId = attempt.resultId,
                score = attempt.score,
                correctCount = attempt.correctCount,
                percentage = percentage,
                xpEarned = xpEarned,
                passed = passed,
            )
        }
    }
