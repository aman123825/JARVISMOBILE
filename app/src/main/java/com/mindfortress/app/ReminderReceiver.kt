package com.mindfortress.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Delivers a user-requested reminder. Android may defer this inexact alarm to save battery.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val message = intent?.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() } ?: return
        val id = intent.getIntExtra(EXTRA_ID, (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders requested by the user through JARVIS"
                }
            )
        }

        val openIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("JARVIS reminder")
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("JARVIS reminder")
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
        }

        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(id, notification)
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.mindfortress.app.ACTION_REMINDER"
        const val EXTRA_MESSAGE = "reminder_message"
        const val EXTRA_ID = "reminder_id"
        private const val CHANNEL_ID = "jarvis_reminders"
    }
}
