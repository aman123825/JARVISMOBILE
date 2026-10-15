package com.mindfortress.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mindfortress.app.ai.CapturedNotification
import com.mindfortress.app.ai.NotificationDigestStore

/**
 * Optional notification listener. It receives notification contents only after the user
 * enables JARVIS in Android's Notification access settings. Contents are kept in memory only.
 */
class JarvisNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return
        NotificationDigestStore.add(
            CapturedNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                postedAtMillis = sbn.postTime
            )
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(android.content.ComponentName(this, JarvisNotificationListenerService::class.java))
    }
}
