package com.example.cybershield.core.database.di

import android.content.Context
import androidx.room.Room
import com.example.cybershield.core.database.CyberShieldDatabase
import com.example.cybershield.core.database.dao.ModuleDao
import com.example.cybershield.core.database.dao.PlaybackPositionDao
import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CyberShieldDatabase =
        Room
            .databaseBuilder(
                context,
                CyberShieldDatabase::class.java,
                "cybershield.db",
            ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideQuizDao(db: CyberShieldDatabase): QuizDao = db.quizDao()

    @Provides
    fun provideQuizResultDao(db: CyberShieldDatabase): QuizResultDao = db.quizResultDao()

    @Provides
    fun provideModuleDao(db: CyberShieldDatabase): ModuleDao = db.moduleDao()

    @Provides
    fun providePlaybackPositionDao(db: CyberShieldDatabase): PlaybackPositionDao = db.playbackPositionDao()

    @Provides
    fun provideQuizAttemptDao(db: CyberShieldDatabase): QuizAttemptDao = db.quizAttemptDao()
}
