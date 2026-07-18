package com.example.cybershield.core.database.di

import android.content.Context
import androidx.room.Room
import com.example.cybershield.core.database.CyberShieldDatabase
import com.example.cybershield.core.database.MIGRATION_8_9
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
            )
            .addMigrations(MIGRATION_8_9)
            // NOTE: this used to be fallbackToDestructiveMigration(dropAllTables = true),
            // which drops every table — including quiz_results rows with
            // synced = false and quiz_attempts rows with provisional = true,
            // i.e. quiz answers a user has already taken that just haven't
            // reached the server yet — on *any* version bump, with no
            // warning. A user who upgrades mid-sync silently loses that
            // attempt forever.
            //
            // fallbackToDestructiveMigrationOnDowngrade only wipes on a
            // downgrade (version goes backwards, e.g. reinstalling an older
            // debug build), which has no unsynced-data risk since a lower
            // version can't have written the newer schema's rows anyway.
            // A forward version bump with no matching Migration now throws
            // IllegalStateException at startup instead of eating data —
            // that's a build-time signal to write a real Migration, not a
            // runtime data-loss bug.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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
