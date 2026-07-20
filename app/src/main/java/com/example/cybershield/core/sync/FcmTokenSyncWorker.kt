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
import androidx.work.workDataOf
import com.example.cybershield.core.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class FcmTokenSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val userRepository: UserRepository,
        private val firebaseAuth: FirebaseAuth,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
            // The user may not be signed in yet when a token refresh arrives
            // (e.g. right after install, before login). There's nothing to
            // attach it to yet, so this isn't a failure — just nothing to do.
            val uid = firebaseAuth.currentUser?.uid ?: return Result.success()

            return try {
                userRepository.updateFcmToken(uid, token)
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        }

        companion object {
            private const val KEY_TOKEN = "fcm_token"
            private const val WORK_NAME = "FcmTokenSyncWorker"
            private const val MAX_RETRIES = 3

            fun enqueue(
                context: Context,
                token: String,
            ) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<FcmTokenSyncWorker>()
                        .setInputData(workDataOf(KEY_TOKEN to token))
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            WorkRequest.MIN_BACKOFF_MILLIS,
                            TimeUnit.MILLISECONDS,
                        ).build()

                // REPLACE: a newer token always supersedes a pending sync of an older one.
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
