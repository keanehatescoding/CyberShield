package com.example.cybershield.core.domain.model

/**
 * A provisional (offline-graded) quiz attempt where every answer now has a
 * server verdict — ready for FinalizeQuizAttemptsUseCase to award XP/badge/
 * certificate off a fully-verified score and flip it to final.
 */
data class ReadyToFinalizeAttempt(
    val resultId: String,
    val userId: String,
    val quizId: String,
    val moduleId: String,
    val moduleName: String,
    val quizTitle: String,
    // Recomputed server-side-verdict score/correctCount — NOT the optimistic
    // in-session values, since those never included points for answers that
    // were still pending when the quiz screen showed its summary.
    val score: Int,
    val totalQuestions: Int,
    val correctCount: Int,
)
