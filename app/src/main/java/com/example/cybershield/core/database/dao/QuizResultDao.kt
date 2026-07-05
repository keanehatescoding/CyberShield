package com.example.cybershield.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.domain.model.QuizResultHistoryItem

/**
 * A single past answer joined with the module it belongs to, for display in
 * the quiz history screen. `moduleTitle` falls back to null if the module was
 * later deleted from the cache — the UI shows a generic label in that case.
 */
data class QuizResultHistoryRow(
    @Embedded
    val result: QuizResultEntity,
    val moduleTitle: String?,
) {
    fun toDomain(): QuizResultHistoryItem =
        QuizResultHistoryItem(
            localId = result.localId,
            quizId = result.quizId,
            moduleId = result.moduleId,
            moduleTitle = moduleTitle ?: "Unknown module",
            isCorrect = result.isCorrect,
            selectedAnswer = result.selectedAnswer,
            answeredAt = result.answeredAt,
            synced = result.synced,
        )
}

@Dao
interface QuizResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: QuizResultEntity): Long

    /** SyncWorker fetches these to send to validateAnswersBatch. */
    @Query("SELECT * FROM quiz_results WHERE synced = 0")
    suspend fun getPendingResults(): List<QuizResultEntity>

    /**
     * SyncWorker calls this after validateAnswersBatch grades a row —
     * records the server's verdict and marks it synced in one step. This
     * replaces the old markSynced (which just flipped a bit after a raw
     * client push); now the grade itself only ever arrives from the server.
     */
    @Query(
        """
        UPDATE quiz_results
        SET isCorrect = :isCorrect, explanation = :explanation, synced = 1
        WHERE localId = :localId
        """,
    )
    suspend fun markGraded(
        localId: Long,
        isCorrect: Boolean,
        explanation: String,
    )

    @Query("SELECT * FROM quiz_results WHERE userId = :userId")
    suspend fun getResultsForUser(userId: String): List<QuizResultEntity>

    /**
     * Paged answer history for a user, newest first, with the owning
     * module's title joined in for display. Room re-runs this automatically
     * whenever `quiz_results` or `modules` changes underneath it.
     *
     * Ordered by `localId` (the auto-increment PK) rather than `answeredAt`
     * (a wall-clock timestamp), since `answeredAt` is not monotonic — device
     * clock changes or timezone shifts can reorder history unexpectedly.
     */
    @Query(
        """
        SELECT quiz_results.*, modules.title AS moduleTitle
        FROM quiz_results
        LEFT JOIN modules ON modules.id = quiz_results.moduleId
        WHERE quiz_results.userId = :userId
        ORDER BY quiz_results.localId DESC
        """,
    )
    fun getResultsForUserPaged(userId: String): PagingSource<Int, QuizResultHistoryRow>

    @Query("DELETE FROM quiz_results WHERE synced = 1")
    suspend fun deleteSyncedResults()

    @Query("DELETE FROM quiz_results WHERE localId IN (:ids)")
    suspend fun deleteByLocalIds(ids: List<Long>)
}
