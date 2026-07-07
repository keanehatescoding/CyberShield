package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.sync.NetworkMonitor
import javax.inject.Inject

/**
 * Submits a selected answer for grading. Grading itself ALWAYS happens
 * server-side (in the validateAnswer / validateAnswersBatch Cloud
 * Functions) — this use case never computes isCorrect locally.
 *
 * - Online: calls validateAnswer immediately and returns the graded result,
 *   so the UI can show correct/incorrect feedback right away.
 * - Offline: caches the raw selectedIndex in Room and returns
 *   Result.Success(null) to signal "saved, grading deferred" — the UI shows
 *   a neutral "saved — you'll see the result once you're back online" state.
 *   SyncQuizResultsWorker grades it later via validateAnswersBatch, and
 *   FinalizeQuizAttemptsUseCase awards XP/badge/certificate once every
 *   answer tagged with [resultId] has a verdict.
 */
class SubmitAnswerUseCase
    @Inject
    constructor(
        private val quizRepository: QuizRepository,
        private val networkMonitor: NetworkMonitor,
    ) {
        suspend operator fun invoke(
            quizId: String,
            resultId: String,
            question: Question,
            selectedIndex: Int,
            selectedAnswer: String,
            userId: String,
            timeRemaining: Int,
        ): Result<AnswerValidation?> {
            if (!networkMonitor.isCurrentlyOnline()) {
                quizRepository.cachePendingAnswer(
                    userId = userId,
                    resultId = resultId,
                    quizId = quizId,
                    questionId = question.id,
                    moduleId = question.moduleId,
                    selectedIndex = selectedIndex,
                    selectedAnswer = selectedAnswer,
                    timeRemaining = timeRemaining,
                )
                return Result.Success(null)
            }

            val result =
                quizRepository.validateAnswerOnline(
                    userId = userId,
                    resultId = resultId,
                    quizId = quizId,
                    questionId = question.id,
                    selectedIndex = selectedIndex,
                    selectedAnswer = selectedAnswer,
                    moduleId = question.moduleId,
                    timeRemaining = timeRemaining,
                )

            // A mid-request drop (e.g. wifi died right as the call went out)
            // shouldn't lose the answer — fall back to the offline cache path
            // so it still gets graded once connectivity returns.
            if (result is Result.Error) {
                quizRepository.cachePendingAnswer(
                    userId = userId,
                    resultId = resultId,
                    quizId = quizId,
                    questionId = question.id,
                    moduleId = question.moduleId,
                    selectedIndex = selectedIndex,
                    selectedAnswer = selectedAnswer,
                    timeRemaining = timeRemaining,
                )
                return Result.Success(null)
            }

            return when (result) {
                is Result.Success -> Result.Success(result.data)
                is Result.Error -> result // unreachable — handled above
                Result.Loading -> Result.Success(null) // unreachable — repository never emits this
            }
        }
    }
