package com.mindfortress.app.access

/**
 * Bounded, process-memory-only cache of content that the user explicitly enabled
 * JARVIS to read through Android notification and accessibility settings.
 *
 * No notification bodies or screen text are written to disk or sent to a server.
 */
data class PhoneNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long
)

data class ScreenTextSnapshot(
    val packageName: String,
    val text: String,
    val capturedAtMillis: Long
)

object PhoneAccessStore {
    private const val MAX_NOTIFICATIONS = 40
    private const val MAX_NOTIFICATION_TEXT = 2_400
    private const val MAX_SCREEN_TEXT = 12_000
    private const val SCREEN_TTL_MILLIS = 120_000L

    private val lock = Any()
    private val notifications = ArrayList<PhoneNotification>()

    @Volatile
    private var screenSnapshot: ScreenTextSnapshot? = null

    fun addNotification(notification: PhoneNotification) {
        val safe = notification.copy(
            title = notification.title.take(300),
            text = notification.text.take(MAX_NOTIFICATION_TEXT)
        )
        synchronized(lock) {
            notifications.removeAll { it.key == safe.key }
            notifications.add(safe)
            notifications.sortBy { it.postedAtMillis }
            while (notifications.size > MAX_NOTIFICATIONS) notifications.removeAt(0)
        }
    }

    fun latestNotification(packageName: String? = null): PhoneNotification? =
        synchronized(lock) {
            notifications.asSequence()
                .filter { packageName == null || it.packageName.equals(packageName, ignoreCase = true) }
                .maxByOrNull { it.postedAtMillis }
        }

    fun recentNotifications(limit: Int = 8): List<PhoneNotification> =
        synchronized(lock) {
            notifications.sortedByDescending { it.postedAtMillis }.take(limit.coerceIn(1, 20))
        }

    fun updateScreen(packageName: String, text: String, nowMillis: Long = System.currentTimeMillis()) {
        val clean = text.trim().take(MAX_SCREEN_TEXT)
        if (packageName.isBlank() || clean.isBlank()) return
        screenSnapshot = ScreenTextSnapshot(packageName, clean, nowMillis)
    }

    fun latestScreen(nowMillis: Long = System.currentTimeMillis()): ScreenTextSnapshot? {
        val snapshot = screenSnapshot ?: return null
        if (nowMillis - snapshot.capturedAtMillis > SCREEN_TTL_MILLIS ||
            nowMillis < snapshot.capturedAtMillis
        ) {
            screenSnapshot = null
            return null
        }
        return snapshot
    }

    fun clearInMemoryCache() {
        synchronized(lock) { notifications.clear() }
        screenSnapshot = null
    }
}
