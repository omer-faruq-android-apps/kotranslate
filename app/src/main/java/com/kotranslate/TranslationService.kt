package com.kotranslate

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class TranslationService : Service() {

    companion object {
        private const val TAG = "TranslationService"
        const val ACTION_START = "com.kotranslate.ACTION_START"
        const val ACTION_STOP = "com.kotranslate.ACTION_STOP"
    }

    private var server: TranslationServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startServer()
                return START_STICKY
            }
        }
    }

    private fun startServer() {
        if (server != null) return

        // CRITICAL: Start foreground IMMEDIATELY to prevent Android from killing the service
        // We have only 5 seconds from startForegroundService() call
        val notification = buildNotification("Starting server...")
        startForeground(KOTranslateApp.NOTIFICATION_ID, notification)

        try {
            Log.i(TAG, "Starting translation server on port ${TranslationServer.DEFAULT_PORT}")
            val translationServer = TranslationServer(applicationContext)
            translationServer.start()
            server = translationServer

            acquireLocks()

            // Update notification with actual status
            val updatedNotification = buildNotification()
            startForeground(KOTranslateApp.NOTIFICATION_ID, updatedNotification)

            Log.i(TAG, "Translation server started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            stopSelf()
        }
    }

    private fun acquireLocks() {
        // Keep CPU awake
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KOTranslate::TranslationServer"
            ).apply { 
                acquire()
                Log.d(TAG, "Wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }

        // Keep WiFi active (only if WiFi is enabled)
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock = wifiManager.createWifiLock(
                    wifiMode,
                    "KOTranslate::WiFi"
                ).apply { 
                    acquire()
                    Log.d(TAG, "WiFi lock acquired")
                }
            } else {
                Log.i(TAG, "WiFi is off, skipping WiFi lock (localhost-only mode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WiFi lock (non-critical)", e)
        }

        // Keep WiFi from going idle (only if WiFi is enabled)
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                multicastLock = wifiManager.createMulticastLock(
                    "KOTranslate::Multicast"
                ).apply { 
                    acquire()
                    Log.d(TAG, "Multicast lock acquired")
                }
            } else {
                Log.i(TAG, "WiFi is off, skipping multicast lock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock (non-critical)", e)
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun buildNotification(customText: String? = null): Notification {
        val ip = NetworkUtils.getLocalIpAddress(applicationContext)
        val address = "http://$ip:${TranslationServer.DEFAULT_PORT}"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TranslationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = customText ?: "Connect: $address"

        return NotificationCompat.Builder(this, KOTranslateApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("KOTranslate Server Running")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Stop Server", stopPending)
            .build()
    }

    override fun onDestroy() {
        Log.i(TAG, "Stopping translation server")
        server?.shutdown()
        server = null
        releaseLocks()
        super.onDestroy()
    }
}
