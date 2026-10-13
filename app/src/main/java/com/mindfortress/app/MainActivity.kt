package com.mindfortress.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.service.SituationalGuardianService
import com.mindfortress.app.theme.MindFortressTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install uncaught exception handler to log crashes synchronously before system handling
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in MindFortress", throwable)
            try {
                val prefs = getSharedPreferences("mindfortress_crash_log", Context.MODE_PRIVATE)
                prefs.edit().putString("last_crash", Log.getStackTraceString(throwable)).commit()
            } catch (ignored: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()

        val navigateTo = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        val initialKey = if (navigateTo == "COGNITIVE_GYM") CognitiveGymKey else JarvisHudKey

        try {
            setContent {
                MindFortressTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation(initialKey = initialKey)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing Compose UI", t)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navigateTo = intent.getStringExtra(EXTRA_NAVIGATE_TO)
        val initialKey = if (navigateTo == "COGNITIVE_GYM") CognitiveGymKey else JarvisHudKey

        try {
            setContent {
                MindFortressTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation(initialKey = initialKey)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error updating intent in Compose UI", t)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"

        /**
         * Safely requests battery optimization exemption when triggered by user interaction in Settings.
         */
        fun requestBatteryOptimizationExemption(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Battery optimization request fallback: ${e.message}")
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to open battery settings: ${e2.message}")
                    }
                }
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else true
        }
    }
}
