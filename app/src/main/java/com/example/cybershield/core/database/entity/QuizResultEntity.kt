package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_results")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val userId: String,
    val quizId: String,
    val questionId: String,
    val moduleId: String,
    // Null until the validateAnswer / validateAnswersBatch Cloud Function has
    // graded this answer. The client writes selectedIndex only — it never
    // computes or stores isCorrect itself.
    val isCorrect: Boolean? = null,
    val selectedIndex: Int,
    val selectedAnswer: String,
    val explanation: String? = null,
    val answeredAt: Long,
    // True once the validateAnswer / validateAnswersBatch Cloud Function has
    // graded AND persisted this answer server-side — grading and server
    // persistence happen atomically inside the function, so one flag covers
    // both. False rows are exactly the ones still awaiting sync.
    val synced: Boolean = false,
)
