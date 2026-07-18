package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey val resultId: String,
    val userId: String,
    val quizId: String,
    val moduleId: String,
    val moduleName: String,
    val quizTitle: String,
    val score: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val percentage: Int,
    val xpEarned: Int,
    val passed: Boolean,
    val timeTaken: Long,
    val createdAt: Long,
    // Mirrors QuizResult.provisional — true while any answer in this
    // attempt was graded offline and hasn't synced yet. FinalizeQuizAttemptsUseCase
    // flips this to false once every answer for this resultId has a verdict.
    val provisional: Boolean = false,
    // How many times FinalizeQuizAttemptsUseCase has tried and failed to
    // finalize this attempt server-side. Some failures are transient
    // (network blip, Firestore quota) and worth retrying; others are
    // permanent (e.g. a later retake of the same quiz overwrote this
    // attempt's quizResults docs before it synced, so the server can never
    // again see a complete answer set for it). This count is what lets
    // QuizRepositoryImpl.recordFinalizeFailure tell the two apart and stop
    // retrying after MAX_FINALIZE_FAILURES instead of forever.
    val finalizeFailureCount: Int = 0,
    // Set once finalizeFailureCount crosses the threshold — the attempt is
    // given up on and excluded from getProvisionalAttempts, so it stops
    // being retried on every periodic sync pass.
    val abandoned: Boolean = false,
)
