package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.cybershield.core.database.Converters
import com.example.cybershield.core.domain.model.Question

@Entity(tableName = "quizzes")
@TypeConverters(Converters::class)
data class QuizEntity(
    @PrimaryKey
    val id: String,
    val moduleId: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val order: Int,
    val moduleName: String,
    val quizTitle: String,
) {
    private fun Question.toEntity() =
        QuizEntity(
            id = id,
            moduleId = moduleId,
            text = text,
            options = options,
            correctIndex = correctIndex,
            explanation = explanation,
            moduleName = moduleName,
            quizTitle = quizTitle,
            order = order,
        )

    private fun QuizEntity.toDomain() =
        Question(
            id = id,
            moduleId = moduleId,
            text = text,
            options = options,
            correctIndex = correctIndex,
            explanation = explanation,
            moduleName = moduleName,
            quizTitle = quizTitle,
            order = order,
        )
}
