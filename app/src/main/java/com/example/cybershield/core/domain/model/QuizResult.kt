package com.example.cybershield.core.domain.model

data class QuizResult(
    val quizId: String,
    val score: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val percentage: Int,
    val xpEarned: Int,
    val passed: Boolean,
    val timeTaken: Long, // seconds
    // True if one or more answers were given offline and haven't been
    // graded yet — score/percentage/xpEarned reflect only the answers
    // graded so far and will update once SyncQuizResultsWorker finishes.
    val provisional: Boolean = false,
)
