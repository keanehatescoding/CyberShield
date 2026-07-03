package com.example.cybershield.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.cybershield.core.database.dao.QuizResultDao
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncQuizResultsWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val resultDao: QuizResultDao,
    private val firestore: FirebaseFirestore,
    private val networkMonitor: NetworkMonitor,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        // Guard — don't attempt if offline
        if (!networkMonitor.isCurrentlyOnline()) return Result.retry()
        val pending = resultDao.getPendingResults()
        if (pending.isEmpty()) return Result.success()
        return try {
            // Batch write to Firestore — one commit per chunk, marked as it completes
            val chunks = pending.chunked(BATCH_CHUNK_SIZE)
            for (chunk in chunks) {
                val batch = firestore.batch()
                chunk.forEach { entity ->
                    val ref =
                        firestore
                            .collection("users")
                            .document(entity.userId)
                            .collection("quizResults")
                            .document("${entity.quizId}_${entity.localId}")
                    batch.set(
                        ref,
                        mapOf(
                            "quizId" to entity.quizId,
                            "moduleId" to entity.moduleId,
                            "isCorrect" to entity.isCorrect,
                            "selectedAnswer" to entity.selectedAnswer,
                            "answeredAt" to entity.answeredAt,
                            "syncedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
                batch.commit().await()
                // Mark and delete per-chunk so a later chunk's failure doesn't
                // force re-upload of chunks that already committed successfully.
                resultDao.markSyncedAndDelete(chunk.map { it.localId })
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retry on transient errors (network blip, Firestore quota, etc.)
            // NOTE: runAttemptCount only tracks attempts within *this* enqueued
            // chain. Result.failure() here ends that chain — it does not mean
            // the rows are unsyncable, only that this chain gave up on them.
            // Whether they ever get synced depends entirely on something else
            // enqueueing this worker again later, which is what
            // schedulePeriodicSync() below guarantees.
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "SyncQuizResultsWorker"
        const val PERIODIC_WORK_NAME = "SyncQuizResultsWorker_periodic"
        const val MAX_RETRIES = 3
        const val BATCH_CHUNK_SIZE = 450

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
         * This is the only thing that ever gives them another chance — call
         * it once from Application.onCreate() (or your DI entry point) with
         * ExistingPeriodicWorkPolicy.KEEP so re-registering on every app
         * launch doesn't reset the schedule.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<SyncQuizResultsWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    )
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}