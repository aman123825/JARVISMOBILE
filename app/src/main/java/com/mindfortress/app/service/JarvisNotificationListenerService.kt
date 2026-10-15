package com.mindfortress.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.mindfortress.app.access.PhoneAccessStore
import com.mindfortress.app.access.PhoneNotification

/**
 * Receives notifications only after the user explicitly enables JARVIS in
 * Android's Notification access settings.
 *
 * Recent text is kept in a small in-memory cache (maximum 40 notifications);
 * it is not persisted to disk and never sent to a network endpoint.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach(::cacheNotification)
            Log.i(TAG, "Notification listener connected; active notifications indexed in memory.")
        } catch (error: Exception) {
            Log.w(TAG, "Could not read active notifications: ${error.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        cacheNotification(sbn)
    }

    private fun cacheNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: Bundle.EMPTY
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
            val standardText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val messagingLines = extractMessagingLines(extras)
            val body = sequenceOf(bigText, standardText, lines.joinToString("\n"), messagingLines.joinToString("\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .firstOrNull()
                .orEmpty()

            if (title.isBlank() && body.isBlank()) return
            val appLabel = try {
                val applicationInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                sbn.packageName
            }

            PhoneAccessStore.addNotification(
                PhoneNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = body,
                    postedAtMillis = sbn.postTime
                )
            )
        } catch (error: Exception) {
            Log.w(TAG, "Could not read notification preview: ${error.message}")
        }
    }

    private fun extractMessagingLines(extras: Bundle): List<String> {
        val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return raw.mapNotNull { parcelable ->
            val bundle = parcelable as? Bundle ?: return@mapNotNull null
            bundle.getCharSequence("text")?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.takeLast(8)
    }

    companion object {
        private const val TAG = "JarvisNotificationReader"

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
            ) ?: return false
            val expected = ComponentName(context, JarvisNotificationListenerService::class.java)
                .flattenToString()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
