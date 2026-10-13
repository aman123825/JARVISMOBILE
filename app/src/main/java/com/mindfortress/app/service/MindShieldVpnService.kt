package com.mindfortress.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.mindfortress.app.MainActivity
import com.mindfortress.app.R
import com.mindfortress.app.data.PreferencesManager

class MindShieldVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("MindFortress SafeDNS Shield")
                .addAddress("10.0.0.2", 32)
                // Add Cloudflare 1.1.1.3 Family Filter & CleanBrowsing Adult Filter DNS
                .addDnsServer(BlockerPolicy.SAFE_DNS_PRIMARY)
                .addDnsServer(BlockerPolicy.SAFE_DNS_SECONDARY)
                .addDnsServer(BlockerPolicy.CLEANBROWSING_ADULT_DNS)
                .setBlocking(false)

            // Disallow our own app to prevent loops
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            PreferencesManager.getInstance(this).isWebShieldActive = true
            Log.i(TAG, "MindFortress VPN established with Safe Adult-Filtering DNS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface: ${e.message}", e)
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            PreferencesManager.getInstance(this).isWebShieldActive = false
            Log.i(TAG, "MindFortress VPN stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}", e)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "mindfortress_shield_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MindFortress Focus Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active shield filtering adult content and distracting domains"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.shield_notification_title))
                .setContentText(getString(R.string.shield_notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.shield_notification_title))
                .setContentText(getString(R.string.shield_notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start fallback in VPN: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MindShieldVpn"
        const val ACTION_START = "com.mindfortress.app.ACTION_START_VPN"
        const val ACTION_STOP = "com.mindfortress.app.ACTION_STOP_VPN"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MindShieldVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MindShieldVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
