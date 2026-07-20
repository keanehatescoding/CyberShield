package com.example.cybershield.core.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.example.cybershield.MainActivity
import com.example.cybershield.R
import com.example.cybershield.core.sync.FcmTokenSyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CyberShieldMessagingService : FirebaseMessagingService() {
    @Deprecated("Deprecated in Java")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token persistence must survive this service being destroyed mid-write,
        // so it's handed off to WorkManager instead of a service-scoped coroutine.
        // WorkManager persists the request and retries with backoff even across
        // process death, which a Service-lifetime CoroutineScope cannot do.
        FcmTokenSyncWorker.enqueue(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title =
            message.notification?.title
                ?: message.data["title"]
                ?: return
        val body =
            message.notification?.body
                ?: message.data["body"]
                ?: return
        val type = message.data["type"] ?: TYPE_GENERAL

        showNotification(title, body, type, message.data)
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String,
        data: Map<String, String>,
    ) {
        val channelId = channelIdFor(type)
        createChannelIfNeeded(channelId, labelFor(type))

        val tapIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                // Previously this attached quizId/moduleId as raw putExtra()
                // values, and nothing in the app ever read them.
                // NavigationRoot's LaunchedEffect routes every launch intent
                // through navController.handleDeepLink(intent), which only
                // matches on intent.data against the moduleDeepLinks /
                // quizDeepLinks patterns ("cybershield://module/{moduleId}",
                // "cybershield://quiz/{quizId}" — see NavigationRoot.kt and
                // the AndroidManifest comment). Without intent.data set,
                // handleDeepLink() always no-ops and every notification tap
                // silently landed on Home regardless of type. Setting a real
                // deep-link URI here makes quiz_reminder taps open the quiz
                // and xp_award/badge_award/module-related taps open the
                // module, the same way an external cybershield:// link would.
                data["quizId"]?.let { this.data = deepLinkUri("quiz", it) }
                    ?: data["moduleId"]?.let { this.data = deepLinkUri("module", it) }
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                type.hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(type.hashCode(), notification)
    }

    private fun createChannelIfNeeded(
        channelId: String,
        label: String,
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        val channel =
            NotificationChannel(
                channelId,
                label,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        manager.createNotificationChannel(channel)
    }

    private fun channelIdFor(type: String) =
        when (type) {
            TYPE_QUIZ_REMINDER -> CHANNEL_QUIZ_REMINDER
            TYPE_XP_AWARD -> CHANNEL_XP_AWARD
            TYPE_BADGE_AWARD -> CHANNEL_BADGE_AWARD
            else -> CHANNEL_GENERAL
        }

    private fun labelFor(type: String) =
        when (type) {
            TYPE_QUIZ_REMINDER -> "Quiz Reminders"
            TYPE_XP_AWARD -> "XP Awards"
            TYPE_BADGE_AWARD -> "Badge Awards"
            else -> "General"
        }

    // No serviceScope, no onDestroy override needed — there is no coroutine
    // owned by this component anymore. The token write is WorkManager's job now.

    companion object {
        const val CHANNEL_GENERAL = "cybershield_general"
        const val CHANNEL_QUIZ_REMINDER = "cybershield_quiz_reminder"
        const val CHANNEL_XP_AWARD = "cybershield_xp_award"
        const val CHANNEL_BADGE_AWARD = "cybershield_badge_award"

        const val TYPE_GENERAL = "general"
        const val TYPE_QUIZ_REMINDER = "quiz_reminder"
        const val TYPE_XP_AWARD = "xp_award"
        const val TYPE_BADGE_AWARD = "badge_award"

        /**
         * Builds a `cybershield://<segment>/<id>` deep-link Uri matching the
         * patterns registered in NavigationRoot.kt (moduleDeepLinks /
         * quizDeepLinks). [id] is percent-encoded since it's server/FCM
         * data, not something this code controls the shape of.
         */
        internal fun deepLinkUri(
            segment: String,
            id: String,
        ): Uri = "cybershield://$segment/${Uri.encode(id)}".toUri()
    }
}
