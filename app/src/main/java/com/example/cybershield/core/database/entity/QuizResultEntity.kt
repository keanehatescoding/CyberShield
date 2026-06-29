package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_results")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val userId: String,
    val quizId: String,
    val moduleId: String,
    val isCorrect: Boolean,
    val selectedAnswer: String,
    val answeredAt: Long,
    val synced: Boolean = false, // SyncWorker sets this to true after push
)
