package com.example.cybershield.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cybershield.core.database.dao.ModuleDao
import com.example.cybershield.core.database.dao.PlaybackPositionDao
import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.ModuleEntity
import com.example.cybershield.core.database.entity.PlaybackPositionEntity
import com.example.cybershield.core.database.entity.QuizAttemptEntity
import com.example.cybershield.core.database.entity.QuizEntity
import com.example.cybershield.core.database.entity.QuizResultEntity

@Database(
    entities = [
        QuizEntity::class,
        QuizResultEntity::class,
        ModuleEntity::class,
        PlaybackPositionEntity::class,
        QuizAttemptEntity::class,
    ],
    version = 9,
    // Exported so future version bumps have a real schema history to write
    // Migration objects against, and so migrations can be tested with
    // Room's MigrationTestHelper instead of guessed at. See
    // app/build.gradle.kts for the ksp `room.schemaLocation` arg that
    // controls where these land (app/schemas/), and DatabaseModule for why
    // this matters: destructive fallback silently drops unsynced rows.
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CyberShieldDatabase : RoomDatabase() {
    abstract fun quizDao(): QuizDao

    abstract fun quizResultDao(): QuizResultDao

    abstract fun moduleDao(): ModuleDao

    abstract fun playbackPositionDao(): PlaybackPositionDao

    abstract fun quizAttemptDao(): QuizAttemptDao
}
