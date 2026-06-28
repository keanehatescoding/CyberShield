package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.cybershield.core.database.Converters

@Entity(tableName = "quizzes")
@TypeConverters(Converters::class)
data class QuizEntity(
    @PrimaryKey
    val id:           String,
    val moduleId:     String,
    val text:         String,
    val options:      List<String>,
    val correctIndex: Int,
    val explanation:  String = "",
    val order: Int,
    val moduleName: String
)