package com.example.cybershield.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cybershield.core.database.dao.ModuleDao
import com.example.cybershield.core.database.dao.PlaybackPositionDao
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.ModuleEntity
import com.example.cybershield.core.database.entity.PlaybackPositionEntity
import com.example.cybershield.core.database.entity.QuizEntity
import com.example.cybershield.core.database.entity.QuizResultEntity

@Database(
    entities = [
        QuizEntity::class,
        QuizResultEntity::class,
        ModuleEntity::class,
        PlaybackPositionEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CyberShieldDatabase : RoomDatabase() {
    abstract fun quizDao(): QuizDao

    abstract fun quizResultDao(): QuizResultDao

    abstract fun moduleDao(): ModuleDao

    abstract fun playbackPositionDao(): PlaybackPositionDao
}
