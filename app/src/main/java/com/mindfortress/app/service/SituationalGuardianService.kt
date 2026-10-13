package com.mindfortress.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.mindfortress.app.MainActivity
import com.mindfortress.app.R
import com.mindfortress.app.data.PreferencesManager
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale

class SituationalGuardianService : Service(), TextToSpeech.OnInitListener {

    private lateinit var prefs: PreferencesManager
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenOnStartTime: Long = System.currentTimeMillis()
    private var lastScreenTimeAccumulated: Long = System.currentTimeMillis()
    private val unlockTimestamps = ArrayDeque<Long>()

    // Cooldown trackers to avoid repetitive nagging
    private var lastLateNightPrompt: Long = 0L
    private var lastCompulsivePrompt: Long = 0L
    private var lastProlongedScreenPrompt: Long = 0L
    private var lastDistractionSpokenPrompt: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnStartTime = System.currentTimeMillis()
                    lastScreenTimeAccumulated = screenOnStartTime
                    Log.d(TAG, "Screen turned ON")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (screenOnStartTime > 0L) {
                        val durationMs = System.currentTimeMillis() - lastScreenTimeAccumulated
                        val minutes = (durationMs / 60_000L).toInt()
                        if (minutes > 0) {
                            prefs.addScreenOnMinutes(minutes)
                        }
                    }
                    screenOnStartTime = 0L
                    lastScreenTimeAccumulated = 0L
                    Log.d(TAG, "Screen turned OFF")
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Fired when user actually unlocks phone via fingerprint / face / PIN
                    prefs.incrementDailyUnlocks()
                    onDeviceUnlocked()
                }
            }
        }
    }

    private val screenCheckRunnable = object : Runnable {
        override fun run() {
            if (screenOnStartTime > 0L) {
                val now = System.currentTimeMillis()
                val diffMs = now - lastScreenTimeAccumulated
                val mins = (diffMs / 60_000L).toInt()
                if (mins > 0) {
                    prefs.addScreenOnMinutes(mins)
                    lastScreenTimeAccumulated += mins * 60_000L
                }
            }
            checkProlongedScreenUsage()
            mainHandler.postDelayed(this, 120_000L) // Check every 2 minutes
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferencesManager.getInstance(this)
        tts = TextToSpeech(applicationContext, this)

        // Register dynamic screen lifecycle listener
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screen receiver registration fallback: ${e.message}")
        }

        // Start periodic background screen fatigue check
        mainHandler.postDelayed(screenCheckRunnable, 120_000L)

        Log.i(TAG, "SituationalGuardianService initialized. Background situational guard active.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.02f)
            tts?.setPitch(0.98f) // Calmer, deeper, focused cadence
            isTtsReady = true
            Log.i(TAG, "TTS initialized successfully for background vocal guard.")
        }
    }

    /**
     * Senses device unlock context and enforces discipline heuristics
     */
    private fun onDeviceUnlocked() {
        if (!prefs.isSituationalGuardianActive) return

        val now = System.currentTimeMillis()
        screenOnStartTime = now

        // 1. Sliding Window: Record unlock timestamp and prune older than 10 minutes
        unlockTimestamps.addLast(now)
        val windowLimit = now - (10 * 60 * 1000L)
        while (unlockTimestamps.isNotEmpty() && unlockTimestamps.first < windowLimit) {
            unlockTimestamps.removeFirst()
        }

        // 2. Heuristic A: Circadian & Late-Night Unlock Detection
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val isLateNight = (hour == 22 && minute >= 30) || (hour >= 23) || (hour < 5)
        if (isLateNight && (now - lastLateNightPrompt > 25 * 60 * 1000L)) {
            lastLateNightPrompt = now
            triggerHapticPulse()
            speakWhisper("It is past 10:30 PM. Remember your circadian rhythm and tomorrow's goals. Put the phone down and rest your mind.")
            return
        }

        // 3. Heuristic B: Dopamine Checking Loop (Frequent Unlocks)
        if (unlockTimestamps.size >= 4 && (now - lastCompulsivePrompt > 15 * 60 * 1000L)) {
            lastCompulsivePrompt = now
            triggerHapticPulse()
            speakWhisper("You have unlocked your phone 4 times in 10 minutes. Pause. Take a 4-second breath and return to your objective.")
            return
        }

        // 4. Heuristic C: Monk Mode Lock Reminder
        if (prefs.isMonkModeActive && (now - lastCompulsivePrompt > 20 * 60 * 1000L)) {
            lastCompulsivePrompt = now
            speakWhisper("Deep focus block is active. Guard your attention.")
        }
    }

    /**
     * Checks continuous screen usage to prevent eye strain and mindless scrolling
     */
    private fun checkProlongedScreenUsage() {
        if (!prefs.isSituationalGuardianActive || screenOnStartTime == 0L) return

        val now = System.currentTimeMillis()
        val screenTimeMinutes = (now - screenOnStartTime) / (60 * 1000L)

        if (screenTimeMinutes >= 35 && (now - lastProlongedScreenPrompt > 30 * 60 * 1000L)) {
            lastProlongedScreenPrompt = now
            triggerHapticPulse()
            speakWhisper("You have been staring at your screen for 35 minutes straight. Take 20 seconds to look away and reset your posture.")
        }
    }

    /**
     * Called directly when Accessibility Service intercepts a blacklisted app or game
     */
    fun onDistractionIntercepted(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastDistractionSpokenPrompt < 3_000L) return
        lastDistractionSpokenPrompt = now

        triggerHapticPulse()

        val simpleName = packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        val prompt = when {
            packageName.contains("game", ignoreCase = true) || packageName.contains("play", ignoreCase = true) ->
                "Game intercepted. Keep your mind sharp and return to your goal."
            packageName.contains("instagram", ignoreCase = true) || packageName.contains("tiktok", ignoreCase = true) ->
                "Feed distraction blocked. Protect your focus."
            else ->
                "Distraction blocked. Returning to your goal."
        }

        speakWhisper(prompt)
    }

    private fun speakWhisper(text: String) {
        if (isTtsReady && tts != null) {
            mainHandler.post {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guardian_whisper")
            }
        }
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic feedback unavailable: ${e.message}")
        }
    }

    private fun startForegroundNotification() {
        val channelId = "situational_guardian_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Situational Focus Guardian",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Autonomous background intelligence protecting user focus and sleep"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("MindFortress Guardian Active")
                .setContentText("Autonomous focus & sleep protection running invisibly in background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("MindFortress Guardian Active")
                .setContentText("Autonomous focus & sleep protection running invisibly in background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start bypassed or unavailable: ${e.message}")
        }
    }

    override fun onDestroy() {
        instance = null
        try {
            if (screenOnStartTime > 0L) {
                val durationMs = System.currentTimeMillis() - lastScreenTimeAccumulated
                val minutes = (durationMs / 60_000L).toInt()
                if (minutes > 0) {
                    prefs.addScreenOnMinutes(minutes)
                }
            }
            unregisterReceiver(screenReceiver)
            mainHandler.removeCallbacks(screenCheckRunnable)
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying SituationalGuardianService: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SituationalGuardian"
        private const val NOTIFICATION_ID = 3003
        const val ACTION_START = "com.mindfortress.app.ACTION_START_GUARDIAN"
        const val ACTION_STOP = "com.mindfortress.app.ACTION_STOP_GUARDIAN"

        @Volatile
        var instance: SituationalGuardianService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, SituationalGuardianService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SituationalGuardianService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
