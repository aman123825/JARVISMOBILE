package com.mindfortress.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mindfortress.app.ai.HandsFreeVoiceService
import com.mindfortress.app.data.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "Device boot/restart event received: $action")

        // Do not restart services on MY_PACKAGE_REPLACED. Android restricts
        // background microphone foreground-service starts on modern releases.
        // The user can explicitly enable hands-free mode after opening JARVIS.
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validActions) {
            val prefs = PreferencesManager.getInstance(context)

            // 1. Restore DNS Web & Adult Shield
            if (prefs.isWebShieldActive) {
                try {
                    MindShieldVpnService.start(context)
                    Log.i(TAG, "MindShieldVpnService auto-started on boot.")
                } catch (e: Exception) {
                    Log.w(TAG, "Error starting VPN on boot: ${e.message}")
                }
            }

            // 2. Restore Situational Background Guardian
            if (prefs.isSituationalGuardianActive) {
                try {
                    SituationalGuardianService.start(context)
                    Log.i(TAG, "SituationalGuardianService auto-started on boot.")
                } catch (e: Exception) {
                    Log.w(TAG, "Error starting Situational Guardian on boot: ${e.message}")
                }
            }

            // Microphone capture is intentionally not auto-started from boot.
            // On current Android versions, microphone foreground services must be
            // started from an eligible visible/user-initiated context. Launch JARVIS
            // and enable hands-free listening explicitly from its UI.
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
