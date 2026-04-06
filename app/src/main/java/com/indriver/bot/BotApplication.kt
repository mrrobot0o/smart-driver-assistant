package com.indriver.bot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class BotApplication : Application() {

    companion object {
        const val CHANNEL_ID = "indriver_bot_channel"
        const val CHANNEL_ORDERS = "indriver_orders_channel"
        
        @Volatile
        private var instance: BotApplication? = null
        
        fun getInstance(): BotApplication = instance!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        createNotificationChannels()
        initEncryptedPrefs()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Main service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background bot monitoring service"
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)
            
            // Orders channel
            val ordersChannel = NotificationChannel(
                CHANNEL_ORDERS,
                "Order Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for accepted orders"
                setShowBadge(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(ordersChannel)
        }
    }

    private fun initEncryptedPrefs() {
        try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                this,
                "bot_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular prefs
        }
    }
}
