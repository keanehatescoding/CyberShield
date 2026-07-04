package com.example.cybershield.core.testing.fake

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.Question
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

    // Inspection fields for assertions
    val savedResults = mutableListOf<SavedQuizResult>()
    var shouldReturnError = false
    var errorMessage = "Fake error"
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
        savedResults.clear()
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

    override suspend fun saveQuizResult(
        userId: String,
        quizId: String,
        moduleId: String,
        isCorrect: Boolean,
        selectedAnswer: String,
    ) {
        if (shouldReturnError) throw Exception(errorMessage)
        savedResults.add(
            SavedQuizResult(
                userId = userId,
                quizId = quizId,
                moduleId = moduleId,
                isCorrect = isCorrect,
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

    companion object {
        const val DEFAULT_PASS_MARK = 70
    }

    data class SavedQuizResult(
        val userId: String,
        val quizId: String,
        val moduleId: String,
        val isCorrect: Boolean,
        val selectedAnswer: String,
    )
}
