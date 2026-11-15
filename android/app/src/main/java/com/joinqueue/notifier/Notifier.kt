package com.joinqueue.notifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL_PROGRESS = "joinqueue_progress"
    private const val CHANNEL_SUCCESS = "joinqueue_success"
    const val FG_ID = 1001
    const val JOINED_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Join Queue progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quiet status while retrying Join Queue"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUCCESS,
                "Join Queue success",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alert only when Join Queue succeeds"
            }
        )
    }

    fun foregroundNotification(context: Context, text: String = "Waiting to click Join Queue…") =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Join Queue clicker running")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun updateProgress(context: Context, text: String, silent: Boolean = true) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Join Queue clicker running")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(FG_ID, notification)
    }

    fun showJoined(context: Context) {
        ensureChannel(context)
        NotificationManagerCompat.from(context).cancel(FG_ID)
        val notification = NotificationCompat.Builder(context, CHANNEL_SUCCESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Joined Feather queue")
            .setContentText("Successfully joined the queue.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(JOINED_ID, notification)
    }
}
