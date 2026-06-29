package com.example.cybershield.core.data.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cybershield.core.sync.SyncQuizResultsWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    /**
     * Schedules a periodic background sync that runs every 15 minutes
     * only when the device has network access.
     * Call this once from Application.onCreate() or CyberShieldApp.
     */
    fun schedulePeriodic(workManager: WorkManager) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            PeriodicWorkRequestBuilder<SyncQuizResultsWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            SyncQuizResultsWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // don't restart if already scheduled
            request,
        )
    }
}
