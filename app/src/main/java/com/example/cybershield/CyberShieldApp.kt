package com.example.cybershield

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.cybershield.core.data.di.SyncModule
import com.example.cybershield.core.firebase.AppCheckInstaller
import com.example.cybershield.core.sync.NetworkMonitor
import com.example.cybershield.core.sync.SyncQuizResultsWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CyberShieldApp :
    Application(),
    Configuration.Provider {
    // Hilt-aware WorkerFactory — required for @HiltWorker to work
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        observeConnectivityForSync()
    }

    /**
     * Whenever connectivity is restored, kick off an immediate one-shot sync so
     * any answers cached while offline get graded without waiting for the next
     * periodic (15 min) pass. scheduleImmediateSync() is idempotent (KEEP policy)
     * so repeated transitions don't stack workers.
     */
    private fun observeConnectivityForSync() {
        appScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    SyncQuizResultsWorker.scheduleImmediateSync(applicationContext)
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}
