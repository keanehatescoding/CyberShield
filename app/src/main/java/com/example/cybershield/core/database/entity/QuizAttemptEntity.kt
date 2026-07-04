package com.example.cybershield.core.database.entity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey val resultId: String,
    val quizId: String,
    val score: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val percentage: Int,
    val xpEarned: Int,
    val passed: Boolean,
    val timeTaken: Long,
    val createdAt: Long,
)
