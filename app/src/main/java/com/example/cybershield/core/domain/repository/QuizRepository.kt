package com.example.cybershield.core.domain.repository

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface QuizRepository {
    /** Fetches all questions for a given module from Firestore (or Room cache). Never includes the answer key. */
    suspend fun getQuizzesForModule(quizId: String): Flow<Result<List<Question>>>

    /**
     * Paged history of a user's past quiz answers, newest first, backed by
     * Room. Includes both graded and not-yet-graded (offline, pending) answers.
     */
    fun getQuizResultHistory(userId: String): Flow<PagingData<QuizResultHistoryItem>>

    /** Returns the pass mark percentage for a quiz (default 70). */
    suspend fun getPassMark(quizId: String): Result<Int>

    /**
     * Online path: submits the answer to the validateAnswer Cloud Function
     * for immediate grading, caches the graded result locally (tagged with
     * [resultId] so it can be aggregated later), and returns the server's
     * verdict. This is the ONLY source of truth for isCorrect — nothing in
     * the app computes it locally.
     */
    suspend fun validateAnswerOnline(
        userId: String,
        resultId: String,
        quizId: String,
        questionId: String,
        selectedIndex: Int,
        selectedAnswer: String,
        moduleId: String,
        timeRemaining: Int,
    ): Result<AnswerValidation>

    /**
     * Offline path: caches the raw answer (selectedIndex only — no verdict),
     * tagged with [resultId], in Room. SyncQuizResultsWorker grades it later
     * via validateAnswersBatch once connectivity returns.
     */
    suspend fun cachePendingAnswer(
        userId: String,
        resultId: String,
        quizId: String,
        questionId: String,
        moduleId: String,
        selectedIndex: Int,
        selectedAnswer: String,
        timeRemaining: Int,
    )

    /**
     * Sends every not-yet-graded row to validateAnswersBatch and records the
     * server's verdict for each. Chunked so a later chunk's failure doesn't
     * force re-submission of chunks that already graded successfully.
     * Called by SyncQuizResultsWorker; safe to call when nothing is pending.
     */
    suspend fun syncPendingResults(): Result<Unit>

    /**
     * Persists a completed quiz's summary keyed by a locally-generated
     * resultId, so the result screen can be reached via navigation (and
     * reopened after process death / deep link) without ever trusting a
     * QuizResult that arrived as a raw navigation argument.
     */
    suspend fun saveQuizAttempt(
        resultId: String,
        userId: String,
        moduleId: String,
        moduleName: String,
        quizTitle: String,
        result: QuizResult,
    )

    suspend fun getQuizAttempt(resultId: String): QuizResult?

    /**
     * Attempts that were saved as provisional (one or more answers were
     * graded offline) and now have a server verdict for every answer — i.e.
     * they're ready for FinalizeQuizAttemptsUseCase to award XP/badge/
     * certificate and flip them to final.
     */
    suspend fun getAttemptsReadyToFinalize(): List<ReadyToFinalizeAttempt>

    /** Records the finalized outcome and flips provisional to false so this attempt is never reprocessed. */
    suspend fun finalizeAttempt(
        resultId: String,
        score: Int,
        correctCount: Int,
        percentage: Int,
        xpEarned: Int,
        passed: Boolean,
    )
}
