package com.example.cybershield.core.domain.model

data class QuizResult(
    val quizId:        String,
    val score:         Int,
    val totalQuestions: Int,
    val xpEarned:      Int,
    val passed:        Boolean,
    val timeTaken:     Long,   // seconds
)
