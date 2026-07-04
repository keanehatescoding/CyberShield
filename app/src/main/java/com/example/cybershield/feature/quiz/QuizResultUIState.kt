package com.example.cybershield.feature.quiz

import com.example.cybershield.core.domain.model.QuizResult

sealed class QuizResultUiState {
    data object Loading : QuizResultUiState()
    data class Loaded(val result: QuizResult) : QuizResultUiState()
    data object NotFound : QuizResultUiState()
}