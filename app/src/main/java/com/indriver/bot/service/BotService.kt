package com.indriver.bot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/**
 * Foreground Service that monitors inDriver API for new orders
 */
class BotService : Service() {

    companion object {
        const val TAG = "BotService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "indriver_bot_channel"
        
        private var isRunning = false
        
        fun start(context: Context) {
            val intent = Intent(context, BotService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isRunning = true
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, BotService::class.java)
            context.stopService(intent)
            isRunning = false
        }
        
        fun isRunning(): Boolean = isRunning
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Bot Service Created")
        
        startForeground(NOTIFICATION_ID, createNotification("Monitoring inDriver..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Bot Service Started")
        
        startMonitoring()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Bot Service Destroyed")
        
        scope.cancel()
        monitoringJob?.cancel()
        isRunning = false
    }

    private fun startMonitoring() {
        monitoringJob = scope.launch {
            Log.d(TAG, "Starting inDriver monitoring...")
            
            while (isActive) {
                try {
                    // Check for new orders via API
                    checkForNewOrders()
                    
                    // Wait before next check
                    delay(1000) // Check every 1 second
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring orders", e)
                    delay(5000) // Wait longer on error
                }
            }
        }
    }

    private suspend fun checkForNewOrders() {
        // This would connect to inDriver's internal API
        // The actual implementation depends on reverse engineering the app
        
        // Example approach using network interception:
        // 1. Set up a VPN to intercept traffic
        // 2. Parse inDriver API responses
        // 3. Trigger accept when new order detected
        
        // For now, we rely on Accessibility Service
        // which monitors the inDriver app UI directly
    }

    private fun createNotification(message: String): android.app.Notification {
        val channelId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "inDriver Bot",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            CHANNEL_ID
        } else {
            ""
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("inDriver Bot")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
