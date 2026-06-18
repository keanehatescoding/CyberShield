package com.example.cybershield.core.domain.model

// core/domain/model/Quiz.kt
data class Quiz(
    val id:           String,
    val moduleId:     String,
    val text:         String,
    val options:      List<String>,
    val correctIndex: Int,
    val explanation:  String = "",
){
    /** Convenience — SubmitAnswerUseCase compares against this */
    val correctAnswer: String
        get() = options.getOrElse(correctIndex) { "" }
}