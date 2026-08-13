package com.example.cybershield.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.domain.util.CrashReporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Low-priority retention sweep: quiz_results/quiz_attempts otherwise grow
 * forever for the life of an install (nothing else ever prunes them —
 * QuizResultDao.deleteSyncedResults()/deleteByLocalIds() and
 * QuizAttemptDao.deleteOlderThan() existed but were never invoked from
 * anywhere). Deletes per-question answers and attempt summaries older than
 * [RETENTION_DAYS] — but only ones that are done being processed (finalized
 * or abandoned); a still-provisional attempt is never touched here
 * regardless of age, since getProvisionalAttempts() is the only thing that
 * will ever pick it back up and finish it. See QuizResultDao/QuizAttemptDao
 * kdocs for why the deletes are scoped that way.
 */
@HiltWorker
class PruneQuizHistoryWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val quizResultDao: QuizResultDao,
        private val quizAttemptDao: QuizAttemptDao,
        private val crashReporter: CrashReporter,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val cutoff = System.currentTimeMillis() - RETENTION_DAYS * DAY_MILLIS
            return try {
                // Answers first, then attempts — deleteForFinalizedAttemptsOlderThan
                // reads quiz_attempts to decide what's eligible, so the attempt
                // rows it depends on must still be present when it runs.
                quizResultDao.deleteForFinalizedAttemptsOlderThan(cutoff)
                quizAttemptDao.deleteOlderThan(cutoff)
                Result.success()
            } catch (e: Exception) {
                // Best-effort housekeeping — never worth retrying aggressively
                // or blocking anything on. Still recorded so a persistent
                // failure (e.g. a schema mismatch) doesn't go unnoticed.
                crashReporter.recordException(e)
                Result.failure()
            }
        }

        companion object {
            private const val WORK_NAME = "PruneQuizHistoryWorker_periodic"
            private const val RETENTION_DAYS = 90L
            private const val DAY_MILLIS = 24L * 60 * 60 * 1000

            /** Call once from Application.onCreate() — safe to call on every launch (KEEP policy). */
            fun schedulePeriodic(workManager: WorkManager) {
                val request =
                    PeriodicWorkRequestBuilder<PruneQuizHistoryWorker>(
                        repeatInterval = 1,
                        repeatIntervalTimeUnit = TimeUnit.DAYS,
                    ).setConstraints(Constraints.Builder().build()) // no network needed — local DB only
                        .build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
