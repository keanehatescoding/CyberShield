package com.example.cybershield.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}