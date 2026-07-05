package com.example.cybershield.core.domain.model

/** A single past quiz-answer entry, shown in the quiz history screen. */
data class QuizResultHistoryItem(
    val localId: Long,
    val quizId: String,
    val moduleId: String,
    val moduleTitle: String,
    /** Null means "answered offline, not yet graded" — see QuizResultEntity.synced. */
    val isCorrect: Boolean?,
    val selectedAnswer: String,
    val answeredAt: Long,
    val synced: Boolean,
)
