package com.example.cybershield.feature.quiz

import com.example.cybershield.core.domain.model.QuizResult

sealed interface QuizUiEvent {
    data class AnswerSyncFailed(val message: String) : QuizUiEvent
    data class CertificateGenerationFailed(val message: String) : QuizUiEvent
    data class NavigateToResult(val resultId: String) : QuizUiEvent
}