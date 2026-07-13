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

    /**
     * Marks a row synced after the server rejected it (e.g. its question was
     * deleted server-side). We leave `isCorrect` NULL so the answer counts as
     * neither correct nor graded-wrong, but the row no longer blocks the
     * parent attempt from finalizing — otherwise the whole quiz session would
     * never be scored and the user would lose all XP/certificate for it.
     */
    @Query(
        """
        UPDATE quiz_results
        SET synced = 1
        WHERE localId = :localId
        """,
    )
    suspend fun markSyncFailed(localId: Long)

    @Query("SELECT * FROM quiz_results WHERE userId = :userId")
    suspend fun getResultsForUser(userId: String): List<QuizResultEntity>

    /** All answers belonging to one quiz session — used to recompute a finalized score. */
    @Query("SELECT * FROM quiz_results WHERE resultId = :resultId")
    suspend fun getResultsForAttempt(resultId: String): List<QuizResultEntity>

    /** Zero means every answer in this attempt has a verdict — the attempt is ready to finalize. */
    @Query("SELECT COUNT(*) FROM quiz_results WHERE resultId = :resultId AND synced = 0")
    suspend fun countUnsyncedForAttempt(resultId: String): Int

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
