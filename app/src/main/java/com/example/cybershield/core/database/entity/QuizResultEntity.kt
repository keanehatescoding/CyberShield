package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_results")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    // Links this answer to a specific quiz session (QuizAttemptEntity.resultId),
    // generated once when the quiz starts. Without this, two attempts at the
    // same quizId (a retake) would have no way to be told apart when
    // recomputing a finalized score later.
    val resultId: String,
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
    // Countdown value at the moment of answering — needed to recompute the
    // speed-bonus score for an offline answer once it's graded later
    // (FinalizeQuizAttemptsUseCase), since that bonus can't be derived from
    // isCorrect alone.
    val timeRemaining: Int,
    // True once the validateAnswer / validateAnswersBatch Cloud Function has
    // graded AND persisted this answer server-side — grading and server
    // persistence happen atomically inside the function, so one flag covers
    // both. False rows are exactly the ones still awaiting sync.
    val synced: Boolean = false,
)
