package com.indriver.bot.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferenceManager(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "indriver_bot_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Bot Settings
    var autoBidEnabled: Boolean
        get() = sharedPreferences.getBoolean("auto_bid_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("auto_bid_enabled", value).apply()

    var notificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean("notifications_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("notifications_enabled", value).apply()

    var minPrice: Double
        get() = sharedPreferences.getString("min_price", "0.0")?.toDoubleOrNull() ?: 0.0
        set(value) = sharedPreferences.edit().putString("min_price", value.toString()).apply()

    var maxDistance: Double
        get() = sharedPreferences.getString("max_distance", "10.0")?.toDoubleOrNull() ?: 10.0
        set(value) = sharedPreferences.edit().putString("max_distance", value.toString()).apply()

    // Stats
    fun getAcceptedCount(): Int = sharedPreferences.getInt("accepted_count", 0)
    fun incrementAccepted() = sharedPreferences.edit().putInt("accepted_count", getAcceptedCount() + 1).apply()

    fun getMissedCount(): Int = sharedPreferences.getInt("missed_count", 0)
    fun incrementMissed() = sharedPreferences.edit().putInt("missed_count", getMissedCount() + 1).apply()

    fun getTotalEarnings(): Double = sharedPreferences.getString("total_earnings", "0.0")?.toDoubleOrNull() ?: 0.0
    fun addEarnings(amount: Double) {
        sharedPreferences.edit().putString("total_earnings", (getTotalEarnings() + amount).toString()).apply()
    }

    // Today's stats
    fun getTodayAccepted(): Int = sharedPreferences.getInt("today_accepted", 0)
    fun incrementTodayAccepted() = sharedPreferences.edit().putInt("today_accepted", getTodayAccepted() + 1).apply()

    fun getTodayEarnings(): Double = sharedPreferences.getString("today_earnings", "0.0")?.toDoubleOrNull() ?: 0.0
    fun addTodayEarnings(amount: Double) {
        sharedPreferences.edit().putString("today_earnings", (getTodayEarnings() + amount).toString()).apply()
    }

    // Best day
    fun getBestDay(): String = sharedPreferences.getString("best_day", "N/A") ?: "N/A"
    fun setBestDay(day: String) = sharedPreferences.edit().putString("best_day", day).apply()

    // Win rate
    fun getWinRate(): Double {
        val accepted = getAcceptedCount()
        val missed = getMissedCount()
        val total = accepted + missed
        return if (total > 0) (accepted.toDouble() / total) * 100 else 0.0
    }

    // Reset
    fun resetStats() {
        sharedPreferences.edit().apply {
            putInt("accepted_count", 0)
            putInt("missed_count", 0)
            putString("total_earnings", "0.0")
            putInt("today_accepted", 0)
            putString("today_earnings", "0.0")
            putString("best_day", "N/A")
            apply()
        }
    }
}
