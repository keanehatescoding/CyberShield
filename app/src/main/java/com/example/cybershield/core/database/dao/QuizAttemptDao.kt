package com.example.cybershield.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cybershield.core.database.entity.QuizAttemptEntity

@Dao
interface QuizAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QuizAttemptEntity)

    @Query("SELECT * FROM quiz_attempts WHERE resultId = :resultId")
    suspend fun getById(resultId: String): QuizAttemptEntity?

    /**
     * Attempts still waiting on one or more offline answers to sync/grade.
     * Excludes `abandoned` attempts — those gave up after repeated finalize
     * failures (see recordFinalizeFailure) and would otherwise be retried by
     * every periodic sync pass forever with no way to ever succeed.
     */
    @Query("SELECT * FROM quiz_attempts WHERE provisional = 1 AND abandoned = 0")
    suspend fun getProvisionalAttempts(): List<QuizAttemptEntity>

    /**
     * Records the finalized outcome once every answer in this attempt has a
     * server verdict — see FinalizeQuizAttemptsUseCase. Flips provisional to
     * false so this attempt is never re-processed.
     */
    @Query(
        """
        UPDATE quiz_attempts
        SET score = :score, correctCount = :correctCount, percentage = :percentage,
            xpEarned = :xpEarned, passed = :passed, provisional = 0
        WHERE resultId = :resultId
        """,
    )
    suspend fun finalize(
        resultId: String,
        score: Int,
        correctCount: Int,
        percentage: Int,
        xpEarned: Int,
        passed: Boolean,
    )

    /**
     * Called by QuizRepositoryImpl.recordFinalizeFailure after a failed
     * finalize call. `abandoned` also clears `provisional` in the same
     * statement so getProvisionalAttempts() stops returning this row the
     * moment it gives up — there's no window where a row is both abandoned
     * and still eligible for another retry.
     */
    @Query(
        """
        UPDATE quiz_attempts
        SET finalizeFailureCount = :count, abandoned = :abandoned,
            provisional = CASE WHEN :abandoned THEN 0 ELSE provisional END
        WHERE resultId = :resultId
        """,
    )
    suspend fun updateFinalizeFailure(
        resultId: String,
        count: Int,
        abandoned: Boolean,
    )

    // Optional: prune attempts older than a retention window, e.g. from a WorkManager job
    @Query("DELETE FROM quiz_attempts WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
