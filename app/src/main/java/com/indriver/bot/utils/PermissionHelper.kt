package com.indriver.bot.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object PermissionHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0
        val serviceName = "${context.packageName}/com.indriver.bot.service.InDriverAccessibilityService"
        
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            if (!TextUtils.isEmpty(settingValue)) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                
                while (splitter.hasNext()) {
                    val accessibilityService = splitter.next()
                    if (accessibilityService.equals(serviceName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
