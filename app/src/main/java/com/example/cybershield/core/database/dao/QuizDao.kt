package com.example.cybershield.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cybershield.core.database.entity.QuizEntity

@Dao
interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quizzes: List<QuizEntity>)

    @Query("SELECT * FROM quizzes WHERE moduleId = :moduleId")
    suspend fun getQuizzesForModule(moduleId: String): List<QuizEntity>

    @Query("DELETE FROM quizzes WHERE moduleId = :moduleId")
    suspend fun deleteForModule(moduleId: String)

    @Query("DELETE FROM quizzes")
    suspend fun clearAll()

    // Mirrors ModuleDao.replaceAll's pattern: a plain insertAll() (REPLACE-by-PK)
    // never removes a locally-cached question that's no longer present
    // remotely (deleted or replaced server-side), so it would otherwise
    // linger forever and can resurface via the offline-fallback cache path.
    @Transaction
    suspend fun replaceForModule(
        moduleId: String,
        quizzes: List<QuizEntity>,
    ) {
        deleteForModule(moduleId)
        insertAll(quizzes)
    }
}
