package com.mindfortress.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mindfortress.app.access.PhoneAccessStore
import java.util.Locale

/**
 * A dedicated read-only AccessibilityService for content the user can see.
 * Unlike AppBlockerAccessibilityService, this service never navigates, clicks,
 * blocks applications, or changes UI state. It keeps a bounded text snapshot
 * in process memory for up to two minutes; password nodes are excluded.
 */
class JarvisScreenReaderAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return

        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName || !isReadablePackage(eventPackage)) return

        try {
            val root = rootInActiveWindow ?: return
            val rootPackage = root.packageName?.toString() ?: eventPackage
            if (rootPackage == packageName || !isReadablePackage(rootPackage)) return

            val readableParts = LinkedHashSet<String>()
            collectReadableText(root, readableParts, 0)
            val text = readableParts.joinToString("\n").take(MAX_SCREEN_CHARACTERS)
            if (text.isNotBlank()) {
                PhoneAccessStore.updateScreen(rootPackage, text)
            }
        } catch (error: Exception) {
            Log.d(TAG, "Screen text snapshot unavailable: ${error.message}")
        }
    }

    private fun collectReadableText(
        node: AccessibilityNodeInfo?,
        output: MutableSet<String>,
        depth: Int
    ) {
        if (node == null || depth > MAX_DEPTH || output.sumOf { it.length } >= MAX_SCREEN_CHARACTERS) {
            return
        }

        // Never capture password fields through accessibility.
        if (runCatching { node.isPassword }.getOrDefault(false)) return

        if (runCatching { node.isVisibleToUser }.getOrDefault(true)) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(output::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(output::add)
        }

        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectReadableText(child, output, depth + 1)
            if (output.sumOf { it.length } >= MAX_SCREEN_CHARACTERS) break
        }
    }

    private fun isReadablePackage(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return !lower.contains("inputmethod") &&
            !lower.contains("keyboard") &&
            lower != "com.android.systemui" &&
            lower != "com.samsung.android.honeyboard" &&
            lower != "com.google.android.inputmethod.latin"
    }

    override fun onInterrupt() {
        Log.w(TAG, "Read-only screen reader interrupted by Android.")
    }

    companion object {
        private const val TAG = "JarvisScreenReader"
        private const val MAX_DEPTH = 20
        private const val MAX_SCREEN_CHARACTERS = 12_000

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(
                context,
                JarvisScreenReaderAccessibilityService::class.java
            ).flattenToString()
            return enabledServices.split(':').any {
                it.equals(expected, ignoreCase = true)
            }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
