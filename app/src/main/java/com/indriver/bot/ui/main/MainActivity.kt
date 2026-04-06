package com.indriver.bot.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.indriver.bot.R
import com.indriver.bot.databinding.ActivityMainBinding
import com.indriver.bot.service.BotService
import com.indriver.bot.service.InDriverAccessibilityService
import com.indriver.bot.utils.PermissionHelper
import com.indriver.bot.utils.PreferenceManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceManager
    
    private var isBotRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = PreferenceManager(this)
        
        setupUI()
        checkPermissions()
        loadStats()
    }

    private fun setupUI() {
        // Main power button
        binding.switchAutoAccept.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkAllPermissions()) {
                binding.switchAutoAccept.isChecked = false
                return@setOnCheckedChangeListener
            }
            
            toggleBot(isChecked)
        }
        
        // Settings buttons
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        
        binding.btnStats.setOnClickListener {
            showStatsDialog()
        }
        
        // Permission request
        binding.btnGrantPermissions.setOnClickListener {
            requestAllPermissions()
        }
    }

    private fun toggleBot(enable: Boolean) {
        isBotRunning = enable
        
        if (enable) {
            BotService.start(this)
            updateStatusRunning()
            Toast.makeText(this, "🟢 Bot Started!", Toast.LENGTH_SHORT).show()
        } else {
            BotService.stop(this)
            updateStatusStopped()
            Toast.makeText(this, "🔴 Bot Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusRunning() {
        binding.apply {
            tvStatus.text = "🟢 RUNNING"
            tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.green_500))
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.green_900))
        }
    }

    private fun updateStatusStopped() {
        binding.apply {
            tvStatus.text = "🔴 STOPPED"
            tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.red_500))
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.gray_900))
        }
    }

    private fun updateStatusPermissionNeeded() {
        binding.apply {
            tvStatus.text = "⚠️ PERMISSIONS NEEDED"
            tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.orange_500))
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.orange_900))
        }
    }

    private fun checkPermissions(): Boolean {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = PermissionHelper.isAccessibilityEnabled(this)
        
        if (!hasOverlay || !hasAccessibility) {
            updateStatusPermissionNeeded()
            binding.btnGrantPermissions.visibility = View.VISIBLE
            return false
        }
        
        binding.btnGrantPermissions.visibility = View.GONE
        return true
    }

    private fun checkAllPermissions(): Boolean {
        if (!checkPermissions()) {
            showPermissionExplanation()
            return false
        }
        return true
    }

    private fun requestAllPermissions() {
        // Request Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            return
        }
        
        // Request Accessibility Permission
        if (!PermissionHelper.isAccessibilityEnabled(this)) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, REQUEST_ACCESSIBILITY_PERMISSION)
        }
    }

    private fun showPermissionExplanation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs:\n\n" +
                "📱 Overlay Permission - To show controls\n" +
                "♿ Accessibility Service - To interact with inDriver\n\n" +
                "Please grant these permissions to continue.")
            .setPositiveButton("Grant") { _, _ ->
                requestAllPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val accepted = prefs.getAcceptedCount()
            val missed = prefs.getMissedCount()
            val earnings = prefs.getTotalEarnings()
            
            binding.tvAccepted.text = accepted.toString()
            binding.tvRejected.text = missed.toString()
            binding.tvEarnings.text = "$${String.format("%.2f", earnings)}"
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        val switchAutoBid = dialogView.findViewById<SwitchMaterial>(R.id.switchAutoBid)
        val switchNotifications = dialogView.findViewById<SwitchMaterial>(R.id.switchNotifications)
        val seekMinPrice = dialogView.findViewById<SeekBar>(R.id.seekMinPrice)
        val seekMaxDistance = dialogView.findViewById<SeekBar>(R.id.seekMaxDistance)
        val tvMinPrice = dialogView.findViewById<android.widget.TextView>(R.id.tvMinPriceValue)
        val tvMaxDistance = dialogView.findViewById<android.widget.TextView>(R.id.tvMaxDistanceValue)
        
        // Load current settings
        switchAutoBid.isChecked = prefs.isAutoBidEnabled()
        switchNotifications.isChecked = prefs.areNotificationsEnabled()
        seekMinPrice.progress = prefs.getMinPrice().toInt()
        seekMaxDistance.progress = prefs.getMaxDistance().toInt()
        
        // Update labels
        tvMinPrice.text = "$${seekMinPrice.progress}"
        tvMaxDistance.text = "${seekMaxDistance.progress} km"
        
        // Setup listeners
        seekMinPrice.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvMinPrice.text = "$$progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        seekMaxDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvMaxDistance.text = "$progress km"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⚙️ Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                prefs.setAutoBidEnabled(switchAutoBid.isChecked)
                prefs.setNotificationsEnabled(switchNotifications.isChecked)
                prefs.setMinPrice(seekMinPrice.progress.toDouble())
                prefs.setMaxDistance(seekMaxDistance.progress.toDouble())
                
                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatsDialog() {
        val stats = """
            📊 Statistics
            
            ✅ Orders Accepted: ${prefs.getAcceptedCount()}
            ❌ Orders Missed: ${prefs.getMissedCount()}
            💰 Total Earnings: $${String.format("%.2f", prefs.getTotalEarnings())}
            
            📅 Today's Stats:
            • Accepted: ${prefs.getTodayAccepted()}
            • Earnings: $${String.format("%.2f", prefs.getTodayEarnings())}
            
            🏆 Best Day: ${prefs.getBestDay()}
            📈 Win Rate: ${String.format("%.1f", prefs.getWinRate())}%
        """.trimIndent()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("📈 Statistics")
            .setMessage(stats)
            .setPositiveButton("Reset Stats") { _, _ ->
                prefs.resetStats()
                loadStats()
                Toast.makeText(this, "Stats reset!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_OVERLAY_PERMISSION -> {
                if (Settings.canDrawOverlays(this)) {
                    requestAllPermissions() // Continue to next permission
                }
            }
            REQUEST_ACCESSIBILITY_PERMISSION -> {
                if (PermissionHelper.isAccessibilityEnabled(this)) {
                    checkPermissions()
                    Toast.makeText(this, "✅ All permissions granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        loadStats()
    }

    companion object {
        const val REQUEST_OVERLAY_PERMISSION = 1001
        const val REQUEST_ACCESSIBILITY_PERMISSION = 1002
    }
}
