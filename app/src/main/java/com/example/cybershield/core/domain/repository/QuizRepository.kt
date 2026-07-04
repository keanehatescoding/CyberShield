package com.example.cybershield.core.domain.repository

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface QuizRepository {
    /** Fetches all questions for a given module from Firestore (or Room cache). */
    suspend fun getQuizzesForModule(quizId: String): Flow<Result<List<Question>>>

    /**
     * Paged history of a user's past quiz answers, newest first, backed by
     * Room. Includes both synced and not-yet-synced answers.
     */
    fun getQuizResultHistory(userId: String): Flow<PagingData<QuizResultHistoryItem>>

    /** Returns the pass mark percentage for a quiz (default 70). */
    suspend fun getPassMark(quizId: String): Result<Int>

    /**
     * Persists a single answer result.
     * Writes to Room immediately; SyncQuizResultsWorker pushes to Firestore
     * when the device is online.
     */
    suspend fun saveQuizResult(
        userId: String,
        quizId: String,
        moduleId: String,
        isCorrect: Boolean,
        selectedAnswer: String,
    )

    /**
     * Pushes all pending (unsynced) quiz results to Firestore, committing in
     * chunks so that a later chunk's failure doesn't force re-upload of
     * chunks that already committed successfully. Marks each chunk as
     * synced-and-deleted locally as soon as its commit succeeds.
     * Called by SyncQuizResultsWorker; safe to call when nothing is pending.
     */
    suspend fun syncPendingResults(): Result<Unit>
}
