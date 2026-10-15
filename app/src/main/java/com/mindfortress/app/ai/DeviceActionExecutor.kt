package com.mindfortress.app.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import com.mindfortress.app.MainActivity
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.receiver.FocusUnlockReceiver
import com.mindfortress.app.service.AppBlockerAccessibilityService
import com.mindfortress.app.service.MindShieldVpnService
import java.util.Locale

class DeviceActionExecutor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentPitch: Float = 1.0f
    private var currentSpeechRate: Float = 1.05f

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.w("DeviceActionExecutor", "TextToSpeech init fallback: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(currentSpeechRate)
            try {
                val femaleVoice = tts?.voices?.firstOrNull { 
                    it.name.contains("female", ignoreCase = true) || 
                    it.name.contains("en-us-x-sfg", ignoreCase = true) ||
                    it.name.contains("en-us-x-tpd", ignoreCase = true)
                }
                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                }
            } catch (e: Exception) {
                // fallback
            }
            tts?.setPitch(currentPitch)
            isTtsReady = true
        }
    }

    /**
     * Configures the voice persona to make the voice crisp, authoritative, and natural.
     */
    fun setVoicePersona(pitch: Float = 1.0f, speechRate: Float = 1.05f) {
        currentPitch = pitch
        currentSpeechRate = speechRate
        if (isTtsReady && tts != null) {
            tts?.setPitch(currentPitch)
            tts?.setSpeechRate(currentSpeechRate)
        }
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            // Apply optimal pitch and rate
            tts?.setPitch(currentPitch)
            tts?.setSpeechRate(currentSpeechRate)

            // Clean markdown tokens before speaking
            val clean = text.replace(Regex("[#*`$\\[\\]_]"), "")
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
        }
    }

    /**
     * Speaks long read-only content in bounded chunks so screen text and
     * notification previews are not silently truncated at one TTS utterance.
     */
    fun speakLongText(text: String, maxChunkLength: Int = 2_600) {
        if (!isTtsReady || tts == null || text.isBlank()) return
        tts?.setPitch(currentPitch)
        tts?.setSpeechRate(currentSpeechRate)
        tts?.stop()

        val clean = text.replace(Regex("[#*\u0060$\\[\\]_]" ), "").trim()
        val chunks = mutableListOf<String>()
        var remaining = clean
        while (remaining.length > maxChunkLength) {
            var cut = maxChunkLength
            val newline = remaining.lastIndexOf('\n', maxChunkLength - 1)
            val sentence = maxOf(
                remaining.lastIndexOf('.', maxChunkLength - 1),
                remaining.lastIndexOf('!', maxChunkLength - 1),
                remaining.lastIndexOf('?', maxChunkLength - 1),
                remaining.lastIndexOf(';', maxChunkLength - 1)
            )
            val preferred = maxOf(newline, sentence)
            if (preferred >= maxChunkLength / 2) cut = preferred + 1
            chunks.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)

        chunks.forEachIndexed { index, chunk ->
            tts?.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "jarvis_long_read_$index"
            )
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun activateDeepFocusLock(minutes: Int): String {
        val prefs = PreferencesManager.getInstance(context)
        prefs.isMonkModeActive = true
        prefs.isAppBlockerActive = true
        prefs.isWebShieldActive = true

        // Ensure VPN is running
        MindShieldVpnService.start(context)

        // Schedule automatic unlock via AlarmManager
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val unlockIntent = Intent(context, FocusUnlockReceiver::class.java).apply {
                action = FocusUnlockReceiver.ACTION_UNLOCK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                FocusUnlockReceiver.UNLOCK_REQUEST_CODE,
                unlockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = SystemClock.elapsedRealtime() + (minutes * 60 * 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager?.canScheduleExactAlarms() == true) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager?.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.i("DeviceActionExecutor", "Scheduled focus unlock in $minutes minutes at elapsedRealtime: $triggerTime")
        } catch (e: Exception) {
            Log.e("DeviceActionExecutor", "Failed to schedule exact focus unlock alarm: ${e.message}")
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "🛡️ Strict Focus Locked for $minutes minutes!", Toast.LENGTH_LONG).show()
        }

        return "Focus Shield and Monk Mode locked for the next $minutes minutes. All distracting games and adult sites are blocked."
    }

    fun purgePhoneDistractions(): String {
        val prefs = PreferencesManager.getInstance(context)
        prefs.isAppBlockerActive = true
        prefs.isWebShieldActive = true
        MindShieldVpnService.start(context)
        return "Distraction shields energized. All social feeds, short-form video platforms, and casual games are locked down."
    }

    fun launchApplication(appName: String): Pair<Boolean, String> {
        val target = appName.trim().lowercase()
        val pm = context.packageManager

        // 1. Direct package map for top apps
        val directPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "yt" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "google" to "com.google.android.googlequicksearchbox",
            "spotify" to "com.spotify.music",
            "whatsapp" to "com.whatsapp",
            "camera" to "com.sec.android.app.camera",
            "calculator" to "com.sec.android.app.popupcalculator",
            "settings" to "com.android.settings",
            "clock" to "com.sec.android.app.clockpackage",
            "calendar" to "com.samsung.android.calendar",
            "gallery" to "com.sec.android.gallery3d",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm"
        )

        val targetPkg = directPackages[target]
        if (targetPkg != null) {
            val intent = pm.getLaunchIntentForPackage(targetPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                val msg = "Opening ${appName.replaceFirstChar { it.uppercase() }} now."
                speak(msg)
                return Pair(true, msg)
            }
        }

        // 2. Search installed applications by display label
        try {
            val installedApps = pm.getInstalledApplications(0)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label == target || label.contains(target)) {
                    val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        val appDisplayName = pm.getApplicationLabel(appInfo).toString()
                        val msg = "Opening $appDisplayName now."
                        speak(msg)
                        return Pair(true, msg)
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        val notFoundMsg = "Could not find an installed app matching '$appName' on your Galaxy S25."
        speak(notFoundMsg)
        return Pair(false, notFoundMsg)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
