package com.example.cybershield.core.domain.model

data class Question(
    val id: String,
    val moduleId: String,
    val moduleName: String,
    val quizTitle: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val order: Int,
)
