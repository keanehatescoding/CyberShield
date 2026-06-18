package com.example.cybershield.core.firebase.model

import com.example.cybershield.core.domain.model.Module
import com.google.firebase.firestore.DocumentId

data class ModuleDto(
    @DocumentId
    val id:           String  = "",
    val title:        String  = "",
    val description:  String  = "",
    val videoUrl:     String  = "",
    val thumbnailUrl: String? = null,
    val quizId:       String  = "",
    val xpReward:     Int     = 100,
    val durationMins: Long     = 5,
    val category:     String  = "General",
    val order:        Int     = 0,
    val isNew:        Boolean = false,
    val isPublished: Boolean = true,
) {
    fun toDomain() = Module(
        id = id,
        title = title,
        description = description,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        quizId = quizId,
        xpReward = xpReward,
        durationMins  = durationMins,
        category = category,
        order = order,
        isNew = isNew,
        isPublished = isPublished,
    )
}