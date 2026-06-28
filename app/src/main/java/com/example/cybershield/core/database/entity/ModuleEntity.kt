package com.example.cybershield.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.cybershield.core.domain.model.Module

@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val videoUrl: String,
    val category: String,
    val durationMinutes: Long,
    val order: Int,
    val xpReward: Int,
    val quizId: String,
    val published: Boolean,
    val cachedAt: Long = System.currentTimeMillis(),
    val new: Boolean,
) {
    fun toDomain(): Module = Module(
        id = id,
        title = title,
        description = description,
        thumbnailUrl = thumbnailUrl,
        videoUrl = videoUrl,
        category = category,
        durationMins = durationMinutes,
        order = order,
        xpReward = xpReward,
        quizId = quizId,
        published = published,
        new = new
    )

    companion object {
        fun fromDomain(module: Module): ModuleEntity = ModuleEntity(
            id = module.id,
            title = module.title,
            description = module.description,
            thumbnailUrl = module.thumbnailUrl,
            videoUrl = module.videoUrl,
            category = module.category,
            durationMinutes = module.durationMins,
            order = module.order,
            xpReward = module.xpReward,
            quizId = module.quizId,
            published = module.published,
            new = module.new
        )
    }
}