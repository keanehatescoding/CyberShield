package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    // Mirrors QuizResult.provisional — true while any answer in this
    // attempt was graded offline and hasn't synced yet.
    val provisional: Boolean = false,
)
