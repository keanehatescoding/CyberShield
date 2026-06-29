package com.example.cybershield.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "playback_positions",
    primaryKeys = ["moduleId", "uid"],
)
data class PlaybackPositionEntity(
    val moduleId: String,
    val uid: String,
    val positionMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
