package com.example.cybershield.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.usecase.FinalizeQuizAttemptsUseCase
import com.example.cybershield.core.domain.util.CrashReporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import com.example.cybershield.core.domain.util.Result as DomainResult

@HiltWorker
class SyncQuizResultsWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val quizRepository: QuizRepository,
    private val finalizeQuizAttempts: FinalizeQuizAttemptsUseCase,
    private val networkMonitor: NetworkMonitor,
    private val crashReporter: CrashReporter,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        // Guard — don't attempt if offline
        if (!networkMonitor.isCurrentlyOnline()) return Result.retry()
        // Chunking, the Firestore batch writes, and per-chunk mark-and-delete
        // all live in QuizRepository now — the worker just triggers it and
        // maps the outcome to a WorkManager Result.
        return when (quizRepository.syncPendingResults()) {
            is DomainResult.Success -> {
                // Now that some (maybe all) pending answers have a verdict,
                // check whether any provisional attempt is fully graded and
                // ready to have its XP/badge/certificate awarded. This is
                // deliberately best-effort: a failure here shouldn't turn a
                // successful sync into a retry — the next sync pass (or the
                // periodic safety-net worker) will pick up any attempt that
                // isn't finalized yet.
                try {
                    finalizeQuizAttempts()
                } catch (e: CancellationException) {
                    // Don't swallow cancellation — a WorkManager-cancelled
                    // worker (constraints no longer met, app backgrounded,
                    // etc.) must not be reported as a crash or as success.
                    throw e
                } catch (e: Exception) {
                    // Swallow — see comment above. The answers themselves are
                    // safely synced regardless of whether finalization ran.
                    // Still record it, so a systemic finalization failure
                    // (e.g. every callable invocation erroring) doesn't
                    // silently persist forever with no signal.
                    crashReporter.recordException(e)
                }
                Result.success()
            }

            is DomainResult.Error -> {
                // Retry on transient errors (network blip, Firestore quota, etc.)
                // NOTE: runAttemptCount only tracks attempts within *this* enqueued
                // chain. Result.failure() here ends that chain — it does not mean
                // the rows are unsyncable, only that this chain gave up on them.
                // Whether they ever get synced depends entirely on something else
                // enqueueing this worker again later, which is what
                // SyncModule.schedulePeriodic() guarantees.
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }

            DomainResult.Loading -> Result.retry() // syncPendingResults never emits this
        }
    }

    companion object {
        const val WORK_NAME = "SyncQuizResultsWorker"
        const val PERIODIC_WORK_NAME = "SyncQuizResultsWorker_periodic"
        const val MAX_RETRIES = 3

        /**
         * Fire-and-forget attempt right after a quiz result is recorded, for
         * low latency when the network is already up.
         */
        fun scheduleImmediateSync(context: Context) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<SyncQuizResultsWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    )
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Safety net: rows that hit MAX_RETRIES and returned Result.failure()
         * in some past chain are still sitting in the DB with synced=false.
         * This is the only thing that ever gives them another chance — the
         * periodic worker registered by SyncModule.schedulePeriodic() (15 min,
         * ExistingPeriodicWorkPolicy.KEEP) guarantees these rows get another
         * pass on every app launch without resetting the schedule.
         */
    }
}