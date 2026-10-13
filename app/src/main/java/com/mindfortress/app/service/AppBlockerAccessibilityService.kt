package com.mindfortress.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mindfortress.app.data.MindFortressDatabaseHelper
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.ui.screens.BlockedOverlayActivity

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo

class AppBlockerAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private lateinit var prefs: PreferencesManager
    private var lastBlockedPackage: String? = null
    private var lastBlockedTimestamp: Long = 0L
    private var lastSoftWarningTimestamp: Long = 0L
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager.getInstance(this)
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Throwable) {
            Log.w(TAG, "TextToSpeech init fallback in AccessibilityService: ${e.message}")
        }
        Log.i(TAG, "AppBlockerAccessibilityService initialized")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
            Log.i(TAG, "AppBlockerAccessibilityService connected with interactive content retrieval enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply dynamic accessibility config: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.05f)
            try {
                val femaleVoice = tts?.voices?.firstOrNull { 
                    it.name.contains("female", ignoreCase = true) || 
                    it.name.contains("en-us-x-sfg", ignoreCase = true) ||
                    it.name.contains("en-us-x-tpd", ignoreCase = true)
                }
                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                } else {
                    tts?.setPitch(1.12f)
                }
            } catch (e: Exception) {
                tts?.setPitch(1.12f)
            }
            isTtsReady = true
        }
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(150)
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    private fun speakIntervention(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "app_interceptor_tts")
        }
    }

    /**
     * Gathers visible text, content descriptions, and view IDs recursively from the node hierarchy.
     */
    private fun inspectNodeHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Set<String> {
        if (node == null || depth > 25) return emptySet()
        val elements = mutableSetOf<String>()

        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { elements.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { elements.add(it) }
        node.viewIdResourceName?.trim()?.takeIf { it.isNotEmpty() }?.let { elements.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            elements.addAll(inspectNodeHierarchy(child, depth + 1))
        }
        return elements
    }

    private fun handleSurgicalPackage(packageName: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = inspectNodeHierarchy(rootNode)
        if (nodes.isEmpty()) return false

        when (packageName) {
            PKG_YOUTUBE -> {
                // If user is on normal YouTube watch player or search, allow it.
                // If they click or swipe into "Shorts", immediately intercept!
                val hasShortsTrap = nodes.any { str ->
                    YOUTUBE_TRAP_KEYWORDS.any { kw ->
                        str.contains(kw, ignoreCase = true)
                    }
                }

                val isNormalWatchOrSearch = nodes.any { str ->
                    val lower = str.lowercase()
                    lower.contains("watch_player") ||
                    lower.contains("player_fragment") ||
                    lower.contains("search_edit_text") ||
                    lower.contains("search") ||
                    lower.contains("results")
                } && !nodes.any { it.contains("shorts_container", ignoreCase = true) }

                if (hasShortsTrap && !isNormalWatchOrSearch) {
                    interceptDistraction(
                        packageName = packageName,
                        reason = "YouTube Shorts doom-scroll loop detected. Redirecting to deep study.",
                        isShorts = true
                    )
                    return true
                }
            }

            PKG_INSTAGRAM -> {
                // If on direct messages or profile, allow or give softer warning; if on Reels feed, immediately block!
                val isReelsTrap = nodes.any { str ->
                    INSTAGRAM_TRAP_KEYWORDS.any { kw ->
                        str.contains(kw, ignoreCase = true)
                    }
                }

                val isDirectOrProfile = nodes.any { str ->
                    val lower = str.lowercase()
                    lower.contains("direct") ||
                    lower.contains("message") ||
                    lower.contains("inbox") ||
                    lower.contains("chat") ||
                    lower.contains("profile") ||
                    lower.contains("edit_profile")
                } && !nodes.any { it.contains("reels_tab", ignoreCase = true) || it.contains("clips", ignoreCase = true) }

                if (isReelsTrap && !isDirectOrProfile) {
                    interceptDistraction(
                        packageName = packageName,
                        reason = "Instagram Reels doom-scroll loop detected. Reclaim your standard right now.",
                        isShorts = true
                    )
                    return true
                } else if (isDirectOrProfile) {
                    val now = System.currentTimeMillis()
                    if (now - lastSoftWarningTimestamp > 60_000L) {
                        lastSoftWarningTimestamp = now
                        speakIntervention("Direct message mode active. Keep check under two minutes.")
                    }
                }
            }
        }
        return false
    }

    private fun interceptDistraction(packageName: String, reason: String, isShorts: Boolean) {
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockedTimestamp) < 2000L) {
            return
        }
        lastBlockedPackage = packageName
        lastBlockedTimestamp = now

        Log.w(TAG, "Distraction detected: $packageName (Shorts=$isShorts). Intercepting immediately!")

        // 1. Kick out to Home screen
        performGlobalAction(GLOBAL_ACTION_HOME)

        val overlayIntent = Intent(this, BlockedOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_PACKAGE, packageName)
        }
        startActivity(overlayIntent)

        // 2. Increment stats and persist event to SQLite
        prefs.incrementBlockedCount()
        if (isShorts) {
            prefs.incrementShortsBlocked()
        }
        MindFortressDatabaseHelper.getInstance(this).logBlockEvent(packageName, "Accessibility Rule Triggered")

        // 3. Physical haptic feedback
        triggerHapticPulse()

        // 4. Vocal Audio Interruption
        speakIntervention(reason)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        if (!prefs.isAppBlockerActive && !prefs.isMonkModeActive) return

        val packageName = event.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        // 1. Surgical package inspection for YouTube & Instagram
        if (packageName == PKG_YOUTUBE || packageName == PKG_INSTAGRAM) {
            handleSurgicalPackage(packageName)
            return
        }

        // 2. Full package blocker for general addictive apps & games
        val customBlocked = prefs.getBlockedPackages()
        if (BlockerPolicy.isDistractingPackage(packageName, customBlocked)) {
            val voiceIntervention = when {
                packageName.contains("tiktok") || packageName.contains("musically") ->
                    "Vivek, TikTok blocked. No short-form brain rot. Reclaim your standard right now."
                packageName.contains("reddit") ->
                    "Reddit blocked. Focus on your problem set."
                packageName.contains("game") ->
                    "Gaming intercepted. Cheap dopamine will not get you to the top. Get back to work."
                else ->
                    "Distraction blocked, Vivek. Stay focused on your primary ambition."
            }
            interceptDistraction(packageName = packageName, reason = voiceIntervention, isShorts = false)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "AppBlockerAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "AppBlockerService"
        private const val PKG_YOUTUBE = "com.google.android.youtube"
        private const val PKG_INSTAGRAM = "com.instagram.android"

        // Surgical doom-scroll trap keywords
        private val YOUTUBE_TRAP_KEYWORDS = listOf("Shorts", "shorts_container", "reel", "YouTube Shorts")
        private val INSTAGRAM_TRAP_KEYWORDS = listOf("Reels", "Explore", "reels_tab", "clips")

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${AppBlockerAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
