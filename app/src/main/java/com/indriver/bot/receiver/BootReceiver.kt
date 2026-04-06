package com.indriver.bot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.indriver.bot.service.BotService

/**
 * Receiver to start bot on device boot
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, checking if bot should start...")
            
            // Check if bot was running before reboot
            val prefs = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)
            val shouldStartOnBoot = prefs.getBoolean("auto_start", false)
            
            if (shouldStartOnBoot) {
                Log.d(TAG, "Starting bot service...")
                BotService.start(context)
            }
        }
    }
}
