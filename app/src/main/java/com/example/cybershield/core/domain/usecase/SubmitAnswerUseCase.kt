package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.Quiz
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import javax.inject.Inject

/**
 * Validates the user's selected answer against the correct answer.
 * Returns a Result wrapping a Boolean — true if correct, false otherwise.
 * Also persists the quiz result via the repository for later sync.
 */
class SubmitAnswerUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
) {
    suspend operator fun invoke(
        quiz:            Quiz,
        selectedAnswer:  String,
        userId:          String,
    ): Result<Boolean> =
        try {
            val isCorrect = quiz.correctAnswer == selectedAnswer

            quizRepository.saveQuizResult(
                userId      = userId,
                quizId      = quiz.id,
                moduleId    = quiz.moduleId,
                isCorrect   = isCorrect,
                selectedAnswer = selectedAnswer,
            )

            Result.Success(isCorrect)
        } catch (e: Exception) {
            Result.Error(e)
        }
}
