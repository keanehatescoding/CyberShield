package com.example.cybershield.core.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
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
                data["screen"]?.let { putExtra("screen", it) }
                data["quizId"]?.let { putExtra("quizId", it) }
                data["moduleId"]?.let { putExtra("moduleId", it) }
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
    }
}