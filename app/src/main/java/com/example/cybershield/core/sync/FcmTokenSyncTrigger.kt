package com.example.cybershield.core.sync

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the current FCM token and hands it to [FcmTokenSyncWorker].
 *
 * [CyberShieldMessagingService.onNewToken] only fires when FCM actually
 * rotates the token — typically once, near install time, which is often
 * *before* the user has an account to attach it to. [FcmTokenSyncWorker]
 * no-ops (`Result.success()`, no retry) when nobody is signed in yet, so
 * without this, that token is silently dropped and never retried: the
 * token doesn't change again on its own, so the user never receives push
 * notifications for the lifetime of the install.
 *
 * Call [syncCurrentToken] explicitly whenever a uid becomes available —
 * sign-in, registration, or an already-authenticated cold start — so a
 * pre-login token gets attached as soon as there's an account to attach
 * it to.
 */
@Singleton
class FcmTokenSyncTrigger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val firebaseMessaging: FirebaseMessaging,
    ) {
        suspend fun syncCurrentToken() {
            val token =
                runCatching { firebaseMessaging.token.await() }
                    .onFailure { Log.w(TAG, "Failed to fetch FCM token for sync", it) }
                    .getOrNull()
                    ?: return
            FcmTokenSyncWorker.enqueue(context, token)
        }

        private companion object {
            const val TAG = "FcmTokenSyncTrigger"
        }
    }
