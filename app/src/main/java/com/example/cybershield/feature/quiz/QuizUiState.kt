package com.example.cybershield.feature.quiz

// ── Domain models (also add to :core:domain/model/) ───────────────────
data class Question(
    val id:            String,
    val text:          String,
    val options:       List<String>,
    val correctIndex:  Int,      // kept server-side in prod; here for simplicity
    val explanation:   String = "",
)

data class QuizResult(
    val quizId:        String,
    val score:         Int,
    val totalQuestions: Int,
    val xpEarned:      Int,
    val passed:        Boolean,
    val timeTaken:     Long,   // seconds
)

// ── Quiz state machine ─────────────────────────────────────────────────
sealed class QuizUiState {

    // Fetching questions from Firestore
    data object Loading : QuizUiState()

    // Actively answering questions
    data class Active(
        val question:        Question,
        val questionIndex:   Int,
        val totalQuestions:  Int,
        val score:           Int,
        val timeLeft:        Int,           // countdown in seconds
        val selectedOption:  Int?   = null, // null = not answered yet
        val isAnswered:      Boolean = false,
        val isCorrect:       Boolean? = null,
    ) : QuizUiState() {
        val progress: Float
            get() = (questionIndex + 1).toFloat() / totalQuestions.toFloat()
        val timerProgress: Float
            get() = timeLeft.toFloat() / QuizViewModel.QUESTION_TIME_SECONDS.toFloat()
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