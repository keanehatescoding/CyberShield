package com.example.cybershield.feature.quiz

sealed interface QuizUiEvent {
    data class AnswerSyncFailed(val message: String) : QuizUiEvent
}