package com.mindfortress.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mindfortress.app.data.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "Device boot/restart event received: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
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

            // 3. Microphone capture cannot be reliably started from BOOT_COMPLETED on
            // modern Android releases because RECORD_AUDIO is a while-in-use permission.
            // Keep the preference, but require the user to open JARVIS or enable listening
            // from a visible activity before the microphone foreground service is started.
            if (prefs.isHandsFreeVoiceActive) {
                Log.i(TAG, "Hands-free is enabled but remains stopped after boot; open JARVIS to resume microphone listening.")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
