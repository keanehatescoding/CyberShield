package com.example.cybershield.feature.quiz

import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult

// ── Quiz state machine ─────────────────────────────────────────────────
sealed class QuizUiState {
    // Fetching questions from Firestore
    data object Loading : QuizUiState()

    // Actively answering questions
    data class Active(
        val question: Question,
        val questionIndex: Int,
        val totalQuestions: Int,
        val score: Int,
        val selectedOption: Int? = null,
        val isAnswered: Boolean = false,
        val isCorrect: Boolean? = null,
        val saveFailed: Boolean = false,
    ) : QuizUiState() {
        val progress: Float
            get() = (questionIndex + 1).toFloat() / totalQuestions.toFloat()
    }

    // Quiz finished — show results
    data class Completed(
        val result: QuizResult,
    ) : QuizUiState()

    // Something went wrong
    data class Error(
        val message: String,
    ) : QuizUiState()
}
