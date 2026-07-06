package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.QuizScoring
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.dataOrNull
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
                val percentage =
                    if (attempt.totalQuestions > 0) (attempt.correctCount * 100) / attempt.totalQuestions else 0
                val passed = percentage >= QuizScoring.PASS_PERCENTAGE

                val xpResult = awardXp(userId = attempt.userId, correctCount = attempt.correctCount, totalCount = attempt.totalQuestions)
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
                    // Unlike QuizViewModel.finishQuiz, a certificate failure here
                    // has no UI to report to — there's no active screen watching
                    // this attempt anymore. It's simply left off; the person can
                    // regenerate it from their profile once they notice it's
                    // missing. Surfacing this properly (e.g. a system
                    // notification) is a reasonable follow-up.
                }

                quizRepository.finalizeAttempt(
                    resultId = attempt.resultId,
                    score = attempt.score,
                    correctCount = attempt.correctCount,
                    percentage = percentage,
                    xpEarned = xpResult.dataOrNull ?: 0,
                    passed = passed,
                )
            }
        }
    }
