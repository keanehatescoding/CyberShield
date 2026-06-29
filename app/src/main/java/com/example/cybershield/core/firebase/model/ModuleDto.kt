package com.example.cybershield.core.firebase.model

import androidx.annotation.Keep
import com.example.cybershield.core.domain.model.Module

@Keep
data class ModuleDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val quizId: String = "",
    val xpReward: Int = 100,
    val durationMins: Long = 5,
    val category: String = "General",
    val order: Int = 0,
    val new: Boolean = false,
    val published: Boolean = true,
) {
    fun toDomain() =
        Module(
            id = id,
            title = title,
            description = description,
            videoUrl = videoUrl,
            thumbnailUrl = thumbnailUrl,
            quizId = quizId,
            xpReward = xpReward,
            durationMins = durationMins,
            category = category,
            order = order,
            new = new,
            published = published,
        )
}
