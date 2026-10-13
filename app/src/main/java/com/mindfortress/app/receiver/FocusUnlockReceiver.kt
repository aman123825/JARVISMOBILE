package com.mindfortress.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.mindfortress.app.data.PreferencesManager
import java.util.Locale

class FocusUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Focus lock timer fired. Deactivating shields.")

        // 1. Sets isMonkModeActive, isAppBlockerActive, isWebShieldActive to false in PreferencesManager
        val prefs = PreferencesManager.getInstance(context)
        prefs.isMonkModeActive = false
        prefs.isAppBlockerActive = false
        prefs.isWebShieldActive = false

        // 2. Speaks "Focus session complete. Shields deactivated." via TTS
        val pendingResult = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.speak(
                    "Focus session complete. Shields deactivated.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "focus_unlock_tts"
                )
            } else {
                Log.w(TAG, "TextToSpeech init failed with status: $status")
            }

            handler.postDelayed({
                try {
                    tts?.stop()
                    tts?.shutdown()
                } catch (e: Exception) {
                    Log.w(TAG, "Error shutting down TTS: ${e.message}")
                }
                try {
                    pendingResult.finish()
                } catch (e: Exception) {
                    // Pending result already finished
                }
            }, 4000)
        }
    }

    companion object {
        private const val TAG = "FocusUnlockReceiver"
        const val ACTION_UNLOCK = "com.mindfortress.app.ACTION_FOCUS_UNLOCK"
        const val UNLOCK_REQUEST_CODE = 4001
    }
}
