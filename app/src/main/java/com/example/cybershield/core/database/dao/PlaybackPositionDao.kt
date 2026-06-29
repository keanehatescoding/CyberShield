package com.example.cybershield.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cybershield.core.database.entity.PlaybackPositionEntity

@Dao
interface PlaybackPositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("SELECT positionMs FROM playback_positions WHERE moduleId = :moduleId AND uid = :uid LIMIT 1")
    suspend fun getPosition(
        moduleId: String,
        uid: String,
    ): Long?
}
