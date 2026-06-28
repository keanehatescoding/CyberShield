package com.example.cybershield.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cybershield.core.database.dao.QuizResultDao
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

@HiltWorker
class SyncQuizResultsWorker @AssistedInject constructor(
    @Assisted context:              Context,
    @Assisted workerParams:         WorkerParameters,
    private val resultDao:          QuizResultDao,
    private val firestore:          FirebaseFirestore,
    private val networkMonitor:     NetworkMonitor,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Guard — don't attempt if offline
        if (!networkMonitor.isCurrentlyOnline()) return Result.retry()

        val pending = resultDao.getPendingResults()
        if (pending.isEmpty()) return Result.success()

        val syncedLocalIds = mutableListOf<Long>()

        return try {
            // Batch write to Firestore — one commit per worker run
            val chunks = pending.chunked(BATCH_CHUNK_SIZE)

            for (chunk in chunks) {
                val batch = firestore.batch()

                chunk.forEach { entity ->
                    val ref = firestore
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
                        )
                    )
                }

                batch.commit().await()
                syncedLocalIds.addAll(chunk.map { it.localId })
            }

            resultDao.markSyncedAndDelete(syncedLocalIds)
            Result.success()

        } catch (_: Exception) {
            // Retry on transient errors (network blip, Firestore quota, etc.)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME   = "SyncQuizResultsWorker"
        const val MAX_RETRIES = 3
        const val BATCH_CHUNK_SIZE = 450
    }
}