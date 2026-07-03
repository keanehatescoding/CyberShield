package com.example.cybershield.core.domain.model

import com.example.cybershield.core.database.entity.QuizEntity

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
) {
    fun toEntity() =
        QuizEntity(
            id = id,
            moduleId = moduleId,
            text = text,
            options = options,
            correctIndex = correctIndex,
            explanation = explanation,
            moduleName = moduleName,
            order = order,
            quizTitle = quizTitle,
        )
}
