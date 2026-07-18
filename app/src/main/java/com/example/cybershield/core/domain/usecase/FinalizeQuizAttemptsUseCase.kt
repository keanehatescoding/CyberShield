package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.CrashReporter
import com.example.cybershield.core.domain.util.dataOrNull
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Runs after SyncQuizResultsWorker successfully grades a batch of offline
 * answers. For every attempt that was left provisional (see QuizViewModel /
 * QuizResult.provisional), checks whether every answer in it now has a
 * server verdict; if so, asks the server to finalize the attempt — which
 * recomputes the score from the verified quizResults, awards XP, and (if
 * passed) issues the certificate + CyberDefender badge — then marks the
 * quiz completed locally and flips the attempt to final.
 *
 * XP, the certificate, and the badge are issued *only* by the server (the
 * finalizeQuizAttempt callable); this use case never writes any of them
 * itself, so none can be forged by a malicious client. The callable is
 * idempotent (see finalizeQuizAttempt's quizAttempts marker doc), so a
 * retried pass never double-awards or double-issues.
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
        private val crashReporter: CrashReporter,
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
                    // it simply stays provisional and gets retried next pass. Still
                    // record it so a systemic failure doesn't go unnoticed forever.
                    crashReporter.recordException(e, mapOf("resultId" to attempt.resultId))
                }
            }
        }

        private suspend fun processAttempt(attempt: ReadyToFinalizeAttempt) {
            // Server recomputes the score from the verified quizResults,
            // awards XP, and (if passed) issues the certificate +
            // CyberDefender badge — all atomically and idempotently (see
            // finalizeQuizAttempt's quizAttempts marker doc).
            val finalize = quizRepository.finalizeQuizAttemptServer(attempt.resultId)
            val fr = finalize.dataOrNull
            if (fr == null) {
                // Some failures are transient (network blip, Firestore quota)
                // and worth leaving provisional for the next sync pass to
                // retry. Others are permanent — most commonly, the user
                // retook this same quiz before it synced, and the retake's
                // answers overwrote this attempt's quizResults docs
                // server-side, so finalizeQuizAttempt can never again see a
                // complete answer set for it. Without a limit, that second
                // case retried silently forever. recordFinalizeFailure tracks
                // the count and abandons the attempt once it's clearly not
                // transient, reporting it once instead of losing it silently.
                if (quizRepository.recordFinalizeFailure(attempt.resultId)) {
                    crashReporter.recordException(
                        IllegalStateException("Abandoned quiz attempt after repeated finalize failures"),
                        mapOf("resultId" to attempt.resultId),
                    )
                }
                return
            }
            val xpEarned = fr.xpEarned

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
