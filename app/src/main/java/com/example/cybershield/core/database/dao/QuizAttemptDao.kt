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

    /** Attempts still waiting on one or more offline answers to sync/grade. */
    @Query("SELECT * FROM quiz_attempts WHERE provisional = 1")
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

    // Optional: prune attempts older than a retention window, e.g. from a WorkManager job
    @Query("DELETE FROM quiz_attempts WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
