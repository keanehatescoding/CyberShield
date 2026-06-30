package com.example.cybershield

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.cybershield.core.data.di.SyncModule
import com.example.cybershield.core.firebase.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CyberShieldApp :
    Application(),
    Configuration.Provider {
    // Hilt-aware WorkerFactory — required for @HiltWorker to work
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        // AppCheckInstaller resolves to the debug or release implementation
        // depending on build type — see app/src/debug and app/src/release.
        AppCheckInstaller.install()
        SyncModule.schedulePeriodic(WorkManager.getInstance(this))
    }
}
