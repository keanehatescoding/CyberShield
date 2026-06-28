package com.example.cybershield.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cybershield.core.database.entity.QuizResultEntity

@Dao
interface QuizResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: QuizResultEntity)

    /** SyncWorker fetches these to push to Firestore. */
    @Query("SELECT * FROM quiz_results WHERE synced = 0")
    suspend fun getPendingResults(): List<QuizResultEntity>

    /** SyncWorker marks rows as synced after a successful Firestore write. */

    @Query("UPDATE quiz_results SET synced = 1 WHERE localId IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Transaction
    suspend fun markSyncedAndDelete(localIds: List<Long>){
        markSynced(localIds)
        deleteByLocalIds(localIds)
    }

    @Query("SELECT * FROM quiz_results WHERE userId = :userId")
    suspend fun getResultsForUser(userId: String): List<QuizResultEntity>

    @Query("DELETE FROM quiz_results WHERE synced = 1")
    suspend fun deleteSyncedResults()

    @Query("DELETE FROM quiz_results WHERE localId IN (:ids)")
    suspend fun deleteByLocalIds(ids: List<Long>)
}