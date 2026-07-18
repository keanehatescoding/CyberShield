package com.example.cybershield.core.testing.fake

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizFinalizeResult
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
    val finalizedAttempts = mutableListOf<FinalizedAttempt>()
    val recordedFinalizeFailures = mutableListOf<String>()
    var shouldReturnError = false
    var errorMessage = "Fake error"

    /** Lets tests script whether recordFinalizeFailure(resultId) reports the attempt as newly abandoned. */
    var recordFinalizeFailureResult: (resultId: String) -> Boolean = { false }

    /** Lets tests script the server's verdict per question, mirroring what validateAnswer would return. */
    var validationProvider: (questionId: String, selectedIndex: Int) -> AnswerValidation =
        { questionId, selectedIndex ->
            AnswerValidation(
                questionId = questionId,
                isCorrect = false,
                correctIndex = -1,
                explanation = ""
            )
        }
    var quizResultHistoryProvider: (String) -> Flow<PagingData<QuizResultHistoryItem>> = {
        flowOf(PagingData.empty())
    }

    /** Lets tests script which provisional attempts are ready to be finalized. */
    var readyToFinalizeProvider: () -> List<ReadyToFinalizeAttempt> = { emptyList() }

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
        finalizedAttempts.clear()
        attempts.clear()
        shouldReturnError = false
        readyToFinalizeProvider = { emptyList() }
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
        resultId: String,
        quizId: String,
        questionId: String,
        selectedIndex: Int,
        selectedAnswer: String,
        moduleId: String,
        timeRemaining: Int,
    ): Result<AnswerValidation> {
        if (shouldReturnError) return Result.Error(Exception(errorMessage))
        val validation = validationProvider(questionId, selectedIndex)
        validatedAnswers.add(
            SavedQuizResult(
                userId = userId,
                resultId = resultId,
                quizId = quizId,
                questionId = questionId,
                moduleId = moduleId,
                isCorrect = validation.isCorrect,
                selectedAnswer = selectedAnswer,
                timeRemaining = timeRemaining,
            ),
        )
        return Result.Success(validation)
    }

    override suspend fun cachePendingAnswer(
        userId: String,
        resultId: String,
        quizId: String,
        questionId: String,
        moduleId: String,
        selectedIndex: Int,
        selectedAnswer: String,
        timeRemaining: Int,
    ) {
        pendingAnswers.add(
            SavedQuizResult(
                userId = userId,
                resultId = resultId,
                quizId = quizId,
                questionId = questionId,
                moduleId = moduleId,
                isCorrect = null,
                selectedAnswer = selectedAnswer,
                timeRemaining = timeRemaining,
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
        userId: String,
        moduleId: String,
        moduleName: String,
        quizTitle: String,
        result: QuizResult,
    ) {
        attempts[resultId] = result
    }

    override suspend fun getQuizAttempt(resultId: String): QuizResult? = attempts[resultId]

    override suspend fun getAttemptsReadyToFinalize(): List<ReadyToFinalizeAttempt> =
        readyToFinalizeProvider()

    override suspend fun finalizeAttempt(
        resultId: String,
        score: Int,
        correctCount: Int,
        percentage: Int,
        xpEarned: Int,
        passed: Boolean,
    ) {
        finalizedAttempts.add(
            FinalizedAttempt(
                resultId,
                score,
                correctCount,
                percentage,
                xpEarned,
                passed
            )
        )
        attempts[resultId]?.let { existing ->
            attempts[resultId] =
                existing.copy(
                    score = score,
                    correctCount = correctCount,
                    percentage = percentage,
                    xpEarned = xpEarned,
                    passed = passed,
                    provisional = false,
                )
        }
    }

    override suspend fun finalizeQuizAttemptServer(resultId: String): Result<QuizFinalizeResult> =
        Result.Success(
            QuizFinalizeResult(
                passed = true,
                score = 100,
                correctCount = 1,
                percentage = 100
            )
        )

    override suspend fun recordFinalizeFailure(resultId: String): Boolean {
        recordedFinalizeFailures.add(resultId)
        return recordFinalizeFailureResult(resultId)
    }

    companion object {
        const val DEFAULT_PASS_MARK = 70
    }

    data class SavedQuizResult(
        val userId: String,
        val resultId: String,
        val quizId: String,
        val questionId: String,
        val moduleId: String,
        val isCorrect: Boolean?,
        val selectedAnswer: String,
        val timeRemaining: Int,
    )

    data class FinalizedAttempt(
        val resultId: String,
        val score: Int,
        val correctCount: Int,
        val percentage: Int,
        val xpEarned: Int,
        val passed: Boolean,
    )
}
