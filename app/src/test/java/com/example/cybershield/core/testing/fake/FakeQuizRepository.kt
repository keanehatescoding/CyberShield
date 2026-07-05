package com.example.cybershield.core.testing.fake

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FakeQuizRepository : QuizRepository {
    // Backing store
    private val quizzes = mutableMapOf<String, List<Question>>()
    private val passMarks = mutableMapOf<String, Int>()
    private val attempts = mutableMapOf<String, QuizResult>()

    // Inspection fields for assertions
    val validatedAnswers = mutableListOf<SavedQuizResult>()
    val pendingAnswers = mutableListOf<SavedQuizResult>()
    var shouldReturnError = false
    var errorMessage = "Fake error"

    /** Lets tests script the server's verdict per question, mirroring what validateAnswer would return. */
    var validationProvider: (questionId: String, selectedIndex: Int) -> AnswerValidation = { questionId, selectedIndex ->
        AnswerValidation(questionId = questionId, isCorrect = false, correctIndex = -1, explanation = "")
    }
    var quizResultHistoryProvider: (String) -> Flow<PagingData<QuizResultHistoryItem>> = {
        flowOf(PagingData.empty())
    }

    // --- Test helpers ---

    fun setQuizzesForModule(
        moduleId: String,
        items: List<Question>,
    ) {
        quizzes[moduleId] = items
    }

    fun setPassMark(
        quizId: String,
        passMark: Int,
    ) {
        passMarks[quizId] = passMark
    }

    fun clearAll() {
        quizzes.clear()
        passMarks.clear()
        validatedAnswers.clear()
        pendingAnswers.clear()
        attempts.clear()
        shouldReturnError = false
    }

    // --- QuizRepository implementation ---

    override suspend fun getQuizzesForModule(quizId: String): Flow<Result<List<Question>>> =
        flow {
            if (shouldReturnError) {
                emit(Result.Error(Exception(errorMessage)))
                return@flow
            }
            emit(Result.Success(quizzes[quizId] ?: emptyList()))
        }

    override suspend fun getPassMark(quizId: String): Result<Int> {
        if (shouldReturnError) return Result.Error(Exception(errorMessage))
        return Result.Success(passMarks[quizId] ?: DEFAULT_PASS_MARK)
    }

    override suspend fun validateAnswerOnline(
        userId: String,
        quizId: String,
        questionId: String,
        selectedIndex: Int,
        selectedAnswer: String,
        moduleId: String,
    ): Result<AnswerValidation> {
        if (shouldReturnError) return Result.Error(Exception(errorMessage))
        val validation = validationProvider(questionId, selectedIndex)
        validatedAnswers.add(
            SavedQuizResult(
                userId = userId,
                quizId = quizId,
                questionId = questionId,
                moduleId = moduleId,
                isCorrect = validation.isCorrect,
                selectedAnswer = selectedAnswer,
            ),
        )
        return Result.Success(validation)
    }

    override suspend fun cachePendingAnswer(
        userId: String,
        quizId: String,
        questionId: String,
        moduleId: String,
        selectedIndex: Int,
        selectedAnswer: String,
    ) {
        pendingAnswers.add(
            SavedQuizResult(
                userId = userId,
                quizId = quizId,
                questionId = questionId,
                moduleId = moduleId,
                isCorrect = null,
                selectedAnswer = selectedAnswer,
            ),
        )
    }

    override suspend fun syncPendingResults(): Result<Unit> {
        if (shouldReturnError) return Result.Error(Exception(errorMessage))
        return Result.Success(Unit)
    }

    override fun getQuizResultHistory(userId: String): Flow<PagingData<QuizResultHistoryItem>> =
        quizResultHistoryProvider(userId)

    override suspend fun saveQuizAttempt(
        resultId: String,
        result: QuizResult,
    ) {
        attempts[resultId] = result
    }

    override suspend fun getQuizAttempt(resultId: String): QuizResult? = attempts[resultId]

    companion object {
        const val DEFAULT_PASS_MARK = 70
    }

    data class SavedQuizResult(
        val userId: String,
        val quizId: String,
        val questionId: String,
        val moduleId: String,
        val isCorrect: Boolean?,
        val selectedAnswer: String,
    )
}
