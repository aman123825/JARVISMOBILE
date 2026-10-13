package com.mindfortress.app.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.mindfortress.app.MainActivity
import com.mindfortress.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HandsFreeVoiceService : Service() {

    private lateinit var companionEngine: LocalCompanionEngine
    private lateinit var voiceEngine: NativeSovereignVoiceEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var commandCollectionJob: Job? = null
    private var isDestroyedService = false

    override fun onCreate() {
        super.onCreate()
        companionEngine = LocalCompanionEngine(this)
        voiceEngine = NativeSovereignVoiceEngine(applicationContext)
        Log.i(TAG, "HandsFreeVoiceService initialized with NativeSovereignVoiceEngine.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isDestroyedService = true
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            Log.w(TAG, "HandsFreeVoiceService cannot start: RECORD_AUDIO permission missing.")
            stopSelf()
            return START_NOT_STICKY
        }

        isDestroyedService = false
        if (!startForegroundNotification()) {
            Log.w(TAG, "Foreground microphone notification could not start; refusing background capture.")
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (isDestroyedService) return

        commandCollectionJob?.cancel()
        commandCollectionJob = serviceScope.launch {
            voiceEngine.commands.collect { spokenText ->
                val normalizedCommand = processSpokenCommand(spokenText)
                if (normalizedCommand != null) {
                    Log.i(TAG, "Executing hands-free command: $normalizedCommand")
                    runCatching { voiceEngine.pause() }
                    val response = companionEngine.processInput(normalizedCommand, speakOutput = true)
                    delay(4000L)
                    if (!isDestroyedService) {
                        runCatching { voiceEngine.resume() }
                    }
                } else {
                    Log.d(TAG, "Ignored background chatter: '$spokenText'")
                }
            }
        }

        serviceScope.launch {
            runCatching { voiceEngine.start() }
                .onFailure { Log.e(TAG, "Failed to start native voice engine: ${it.message}") }
        }
    }

    private fun stopListening() {
        commandCollectionJob?.cancel()
        commandCollectionJob = null
        // Use the engine-owned scope; onDestroy cancels serviceScope immediately.
        voiceEngine.closeAsync()
    }

    private fun startForegroundNotification(): Boolean {
        val channelId = "hands_free_jarvis_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Jarvis Hands-Free Voice Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible notification for on-device wake-word listening"
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
                .setContentTitle("JARVIS Hands-Free Listening")
                .setContentText("Listening locally for Hey Jarvis. Tap to open JARVIS or stop listening in the app.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Jarvis Hands-Free Guard Active")
                .setContentText("Listening locally for 'Hey Jarvis' via Sherpa-ONNX. Zero cloud dependency.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Hands-Free listener foreground start failed: ${e.message}")
            false
        }
    }

    override fun onDestroy() {
        stopListening()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "HandsFreeVoiceService"
        const val ACTION_START = "com.mindfortress.app.ACTION_START_HANDSFREE"
        const val ACTION_STOP = "com.mindfortress.app.ACTION_STOP_HANDSFREE"
        private const val NOTIFICATION_ID = 2002

        fun start(context: Context) {
            if (ForegroundVoiceSession.hasActiveUiSession()) {
                Log.i(TAG, "Foreground JARVIS screen owns microphone; background listener start deferred.")
                return
            }
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                Log.w(TAG, "Cannot start HandsFreeVoiceService: RECORD_AUDIO permission not granted.")
                return
            }
            try {
                val intent = Intent(context, HandsFreeVoiceService::class.java).apply { action = ACTION_START }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HandsFreeVoiceService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HandsFreeVoiceService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop HandsFreeVoiceService: ${e.message}")
            }
        }

        /**
         * Translates Hinglish / Hindi commands into standardized English companion instructions.
         */
        fun translateHinglish(input: String): String {
            val lower = input.lowercase().trim()
            return when {
                lower.contains("phone lock kar do") ||
                lower.contains("phone lock karo") ||
                lower.contains("phone lock kardo") -> "lock my phone for 60 minutes"

                lower.contains("padhai shuru karo") ||
                lower.contains("padhai shuru kardo") ||
                lower.contains("study mode lagao") -> "monk mode"

                lower.contains("sun rahe ho") ||
                lower.contains("sun rahi ho") -> "are you listening"

                lower.contains("distraction roko") ||
                lower.contains("distraction band karo") ||
                lower.contains("distraction band kardo") -> "clear phone"

                lower.contains("feynman test lo") ||
                lower.contains("feynman test") -> "feynman"

                else -> input
            }
        }

        /**
         * Checks if the transcribed speech addresses JARVIS or contains a direct action command.
         * Returns normalized command if actionable, or null if it's pure background conversation.
         */
        fun processSpokenCommand(spokenText: String): String? {
            if (spokenText.isBlank()) return null
            val lower = spokenText.lowercase().trim()

            // 1. Check for Hinglish translation first
            val translated = translateHinglish(lower)
            if (translated != lower) {
                return translated
            }

            // 2. Check if addresses JARVIS directly
            val addressesJarvis = lower.contains("jarvis") ||
                    lower.startsWith("hey jarvis") ||
                    lower.startsWith("hi jarvis") ||
                    lower.startsWith("ok jarvis")

            if (addressesJarvis) {
                // Strip wake word prefix to pass clean command to companion engine
                val stripped = lower
                    .replace(Regex("^(?:hey|hi|ok|hello)?\\s*jarvis[,\\s]*", RegexOption.IGNORE_CASE), "")
                    .trim()
                val targetText = if (stripped.isNotEmpty()) stripped else lower
                val finalTranslated = translateHinglish(targetText)
                return if (finalTranslated.isNotBlank()) finalTranslated else "are you listening"
            }

            // 3. Direct action commands without explicit wake word
            val isDirectAction = lower.startsWith("lock") ||
                    lower.startsWith("open ") ||
                    lower.startsWith("launch ") ||
                    lower.startsWith("run ") ||
                    lower.startsWith("start ") ||
                    lower.contains("monk mode") ||
                    lower.contains("deep work") ||
                    lower.contains("clear phone") ||
                    lower.contains("block everything") ||
                    lower.contains("purge") ||
                    lower.contains("feynman") ||
                    lower.contains("quiz me") ||
                    lower.contains("morning briefing") ||
                    lower.contains("morning report") ||
                    lower.contains("daily brief") ||
                    lower.contains("are you listening") ||
                    lower.contains("can you hear me") ||
                    lower.contains("study for") ||
                    lower.contains("drill") ||
                    lower.contains("ambition") ||
                    lower.contains("shield status") ||
                    lower.contains("how many blocked") ||
                    lower.startsWith("set alarm") ||
                    lower.startsWith("create alarm") ||
                    lower.startsWith("wake me") ||
                    lower.contains("timer for") ||
                    lower.startsWith("timer ") ||
                    lower.startsWith("remind me in") ||
                    lower.startsWith("call ") ||
                    lower.startsWith("dial ") ||
                    lower.startsWith("phone ") ||
                    lower.startsWith("text ") ||
                    lower.startsWith("sms ") ||
                    lower.startsWith("message ") ||
                    lower.startsWith("search ") ||
                    lower.startsWith("google ") ||
                    lower.startsWith("look up online ") ||
                    lower.startsWith("find online ") ||
                    lower.startsWith("visit ") ||
                    lower.startsWith("go to ") ||
                    lower.startsWith("navigate to ") ||
                    lower.startsWith("directions to ") ||
                    lower.startsWith("route to ") ||
                    lower.startsWith("take me to ") ||
                    lower.startsWith("open settings") ||
                    lower.startsWith("open wifi settings") ||
                    lower.startsWith("open wi-fi settings") ||
                    lower.startsWith("open bluetooth settings") ||
                    lower.startsWith("open sound settings") ||
                    lower.startsWith("open display settings") ||
                    lower.startsWith("open location settings") ||
                    lower.startsWith("open battery settings") ||
                    lower.startsWith("open accessibility settings") ||
                    lower.startsWith("remember ") ||
                    lower.startsWith("note that ") ||
                    lower.startsWith("save note ") ||
                    lower.startsWith("make a note ") ||
                    lower.startsWith("forget memory ") ||
                    lower.startsWith("forget note ") ||
                    lower.startsWith("add task ") ||
                    lower.startsWith("create task ") ||
                    lower.startsWith("to-do ") ||
                    lower.startsWith("complete task ") ||
                    lower.startsWith("finish task ") ||
                    lower == "show tasks" ||
                    lower == "list tasks" ||
                    lower == "my tasks" ||
                    lower == "what are my tasks" ||
                    lower == "show memories" ||
                    lower == "list memories" ||
                    lower == "what do you remember" ||
                    lower == "show my notes" ||
                    lower == "list notes" ||
                    lower == "help" ||
                    lower == "what can you do" ||
                    lower == "what can jarvis do" ||
                    lower.startsWith("research online ") ||
                    lower.startsWith("research and summarize online ") ||
                    lower.startsWith("find academic papers on ") ||
                    lower.startsWith("find studies on ") ||
                    lower.startsWith("literature review on ") ||
                    lower.startsWith("set volume ") ||
                    lower.startsWith("volume ") ||
                    lower.contains("deep focus") ||
                    lower.contains("focus lock") ||
                    lower == "clear distractions" ||
                    lower == "stop distractions" ||
                    lower == "read notifications" ||
                    lower == "read my notifications" ||
                    lower == "show notifications" ||
                    lower == "list notifications" ||
                    lower == "what did i miss" ||
                    lower == "summarize notifications" ||
                    lower == "summarize my notifications" ||
                    lower == "summarise my notifications" ||
                    lower == "notification summary" ||
                    lower == "clear notification history"

            if (isDirectAction) {
                return lower
            }

            // Otherwise, background chatter not addressing the assistant
            return null
        }
    }
}
