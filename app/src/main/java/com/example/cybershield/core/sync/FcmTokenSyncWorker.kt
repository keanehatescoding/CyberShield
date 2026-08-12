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
import com.example.cybershield.core.domain.util.CrashReporter
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import com.example.cybershield.core.domain.util.Result as DomainResult

@HiltWorker
class FcmTokenSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val userRepository: UserRepository,
        private val firebaseAuth: FirebaseAuth,
        private val crashReporter: CrashReporter,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
            // The user may not be signed in yet when a token refresh arrives
            // (e.g. right after install, before login). There's nothing to
            // attach it to yet, so this isn't a failure — just nothing to do.
            val uid = firebaseAuth.currentUser?.uid ?: return Result.success()

            // updateFcmToken catches its own exceptions internally (see
            // UserRepositoryImpl.updateFcmToken's resultOf wrapper) and
            // returns Result.Error rather than throwing, so a try/catch here
            // never actually caught anything — every failed write was
            // silently treated as WorkManager success, with no retry and no
            // telemetry. Check the returned Result instead.
            return when (userRepository.updateFcmToken(uid, token)) {
                is DomainResult.Success -> Result.success()
                is DomainResult.Error -> {
                    if (runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        // Unlike SyncQuizResultsWorker/FinalizeQuizAttemptsUseCase,
                        // this previously reported nothing on terminal failure —
                        // a systemic token-registration failure (e.g. a bad
                        // Firestore rule change) would silently break push
                        // notification delivery with zero signal in Crashlytics.
                        crashReporter.recordException(
                            IllegalStateException("FCM token sync failed after $runAttemptCount attempts"),
                        )
                        Result.failure()
                    }
                }
                DomainResult.Loading -> Result.retry() // updateFcmToken never emits this
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
