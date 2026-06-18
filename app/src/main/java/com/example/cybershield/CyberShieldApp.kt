package com.example.cybershield

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.cybershield.core.di.SyncModule
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CyberShieldApp : Application(), Configuration.Provider {

    // Hilt-aware WorkerFactory — required for @HiltWorker to work
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workManager:   WorkManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        SyncModule.schedulePeriodic(workManager)
    }
}