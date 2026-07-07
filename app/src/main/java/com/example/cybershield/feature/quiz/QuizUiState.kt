package com.example.cybershield.feature.quiz

import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult

// ── Quiz state machine ─────────────────────────────────────────────────
sealed class QuizUiState {
    // Fetching questions from Firestore
    data object Loading : QuizUiState()

    // Actively answering questions.
    // NOTE: countdown timer state lives in QuizViewModel.timeLeft (a separate
    // StateFlow), not here — see QuizViewModel for why (keeps per-second
    // ticks from recomposing/re-diffing this whole state).
    data class Active(
        val question: Question,
        val questionIndex: Int,
        val totalQuestions: Int,
        val score: Int,
        val selectedOption: Int? = null, // null = not answered yet
        val isAnswered: Boolean = false,
        // Null while awaiting the server's verdict — either the online call
        // hasn't returned yet, or (isPending == true) the device is offline
        // and grading is deferred until sync.
        val isCorrect: Boolean? = null,
        // True when this answer was cached offline rather than graded
        // immediately — the UI shows a neutral "saved, pending" state
        // instead of a green/red reveal.
        val isPending: Boolean = false,
        // Only ever populated from the server's response, and only after
        // the user has already submitted an answer — the client never has
        // this before answering.
        val revealedCorrectIndex: Int? = null,
        val revealedExplanation: String? = null,
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
