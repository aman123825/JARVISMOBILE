package com.mindfortress.app.ai

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import java.util.LinkedHashMap

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long
)

/**
 * In-memory digest of notifications after the user explicitly grants Android notification access.
 * Notification text is not written to SharedPreferences/files and this object has no network path.
 */
object NotificationDigestStore {
    private const val MAX_ENTRIES = 60
    private const val MAX_TITLE_LENGTH = 180
    private const val MAX_TEXT_LENGTH = 900
    private val lock = Any()
    private val entries = LinkedHashMap<String, CapturedNotification>()

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        if (enabled.isBlank()) return false
        val target = ComponentName(context, com.mindfortress.app.service.JarvisNotificationListenerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
    }

    fun add(entry: CapturedNotification) {
        val title = entry.title.trim().take(MAX_TITLE_LENGTH)
        val text = entry.text.trim().take(MAX_TEXT_LENGTH)
        if (title.isBlank() && text.isBlank()) return
        synchronized(lock) {
            entries.remove(entry.key)
            entries[entry.key] = entry.copy(title = title, text = text)
            while (entries.size > MAX_ENTRIES) {
                val firstKey = entries.keys.firstOrNull() ?: break
                entries.remove(firstKey)
            }
        }
    }

    fun latest(limit: Int = 25): List<CapturedNotification> = synchronized(lock) {
        entries.values.toList().takeLast(limit.coerceIn(1, MAX_ENTRIES)).asReversed()
    }

    fun clear() = synchronized(lock) { entries.clear() }

    fun renderLatest(limit: Int = 25): String {
        val items = latest(limit)
        if (items.isEmpty()) return "No notification content has been captured since JARVIS notification access was enabled."
        return items.mapIndexed { index, notification ->
            val time = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT
            ).format(java.util.Date(notification.postedAtMillis))
            buildString {
                append(index + 1).append(". [").append(notification.packageName).append("] ")
                if (notification.title.isNotBlank()) append(notification.title)
                if (notification.text.isNotBlank()) {
                    if (notification.title.isNotBlank()) append(": ")
                    append(notification.text)
                }
                append(" (").append(time).append(")")
            }
        }.joinToString("\n")
    }
}
