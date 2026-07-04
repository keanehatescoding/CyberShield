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

    // Optional: prune attempts older than a retention window, e.g. from a WorkManager job
    @Query("DELETE FROM quiz_attempts WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
