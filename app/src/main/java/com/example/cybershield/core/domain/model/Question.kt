package com.example.cybershield.core.domain.model

/**
 * Client-facing question shape. Deliberately has NO `correctIndex` or
 * `explanation` field — those live server-side only, in the `answerKeys`
 * Firestore collection (unreadable by any client) and are revealed to a
 * specific user only via the `validateAnswer` / `validateAnswersBatch`
 * Cloud Functions, and only for a question that user has just answered.
 * See functions/src/grading.ts.
 */
data class Question(
    val id: String,
    val moduleId: String,
    val moduleName: String,
    val quizTitle: String,
    val text: String,
    val options: List<String>,
    val order: Int,
)

/** Server-graded outcome for one submitted answer. Never contains anything the client didn't already reveal by answering. */
data class AnswerValidation(
    val questionId: String,
    val isCorrect: Boolean,
    val correctIndex: Int,
    val explanation: String,
)
