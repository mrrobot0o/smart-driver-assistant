package com.indriver.bot

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Accessibility Service that monitors inDriver app and auto-accepts orders
 */
class InDriverAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "InDriverBot"
        const val CHANNEL_ID = "indriver_bot_channel"
        const val NOTIFICATION_ID = 1001
        
        const val PACKAGE_INDRIVER = "sinet.startup.inDriver"
        const val PACKAGE_INDRIVER_ALT = "com.indriver"
        
        // Settings
        var isEnabled = false
        var autoAcceptEnabled = true
        var minPrice = 0.0
        var maxDistance = 0.0
        var targetAreas = listOf<String>()
        
        private var instance: InDriverAccessibilityService? = null
        
        fun getInstance(): InDriverAccessibilityService? = instance
        
        fun isServiceEnabled(context: Context): Boolean {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            return accessibilityManager.isTouchExplorationEnabled
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Stats
    private var totalOrdersDetected = 0
    private var ordersAccepted = 0
    private var ordersMissed = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")
        
        // Configure service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
            packageNames = arrayOf(PACKAGE_INDRIVER, PACKAGE_INDRIVER_ALT)
        }
        serviceInfo = info
        
        createNotificationChannel()
        showNotification("Bot Active", "Monitoring inDriver for orders...")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isEnabled || !autoAcceptEnabled) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // Only process inDriver events
        if (packageName != PACKAGE_INDRIVER && packageName != PACKAGE_INDRIVER_ALT) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowChange(event)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    private fun handleNotification(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: ""
        Log.d(TAG, "Notification: $text")
        
        // Check for new order notification
        if (text.contains("طلب", ignoreCase = true) || 
            text.contains("order", ignoreCase = true) ||
            text.contains("ride", ignoreCase = true) ||
            text.contains("طلب جديد", ignoreCase = true)) {
            
            totalOrdersDetected++
            Log.d(TAG, "New order detected! Total: $totalOrdersDetected")
            
            // Try to accept automatically
            scope.launch {
                delay(100) // Small delay for UI to update
                tryAcceptOrder()
            }
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        if (!autoAcceptEnabled) return
        
        // Check if we're on a order screen
        val className = event.className?.toString() ?: ""
        
        if (className.contains("Order", ignoreCase = true) ||
            className.contains("Request", ignoreCase = true) ||
            className.contains("Bid", ignoreCase = true)) {
            
            scope.launch {
                delay(200)
                tryAcceptOrder()
            }
        }
    }

    private suspend fun tryAcceptOrder() {
        if (!isEnabled || !autoAcceptEnabled) return
        
        val root = rootInActiveWindow ?: return
        
        Log.d(TAG, "Trying to find and accept order...")
        
        // Common accept button texts in inDriver
        val acceptTexts = listOf(
            "قبول", "Accept", "Принять", "принять", "ACEPTAR", " kabul", 
            "ACCEPT", "Принять заказ", "Take order", "TAKE"
        )
        
        // Common bid button texts
        val bidTexts = listOf(
            "مزايدة", "Bid", "Ставка", "BID", "ставка", "Ofertar"
        )
        
        // Try to find accept button
        for (text in acceptTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        val accepted = performClick(node)
                        if (accepted) {
                            ordersAccepted++
                            Log.d(TAG, "Order ACCEPTED! Total accepted: $ordersAccepted")
                            showToast("✅ Order Accepted!")
                            showNotification("Order Accepted!", "Auto-accepted order #$ordersAccepted")
                            return
                        }
                    }
                }
            }
        }
        
        // Try to find bid button (if accept not found)
        for (text in bidTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        val clicked = performClick(node)
                        if (clicked) {
                            Log.d(TAG, "Bid button clicked, entering price...")
                            // After clicking bid, try to submit
                            delay(300)
                            trySubmitBid()
                            return
                        }
                    }
                }
            }
        }
        
        // Try to find by view ID
        val acceptIds = listOf(
            "btn_accept", "accept_button", "button_accept", "btnAccept",
            "order_accept", "take_order", "btnTake"
        )
        
        for (id in acceptIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        val accepted = performClick(node)
                        if (accepted) {
                            ordersAccepted++
                            Log.d(TAG, "Order ACCEPTED via ID! Total: $ordersAccepted")
                            showToast("✅ Order Accepted!")
                            return
                        }
                    }
                }
            }
        }
        
        ordersMissed++
        Log.d(TAG, "Could not find accept button. Missed: $ordersMissed")
    }

    private suspend fun trySubmitBid() {
        val root = rootInActiveWindow ?: return
        
        // Try to find submit/confirm button after entering bid
        val submitTexts = listOf("تأكيد", "Confirm", "Submit", "Подтвердить", "CONFIRM", "OK", "إرسال")
        
        for (text in submitTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    val clicked = performClick(node)
                    if (clicked) {
                        ordersAccepted++
                        showToast("✅ Bid Submitted!")
                        return
                    }
                }
            }
        }
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked node: ${node.text} (${node.viewIdResourceName})")
                true
            } else if (node.parent != null) {
                performClick(node.parent)
            } else {
                // Try gesture click
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                performGestureClick(bounds.exactCenterX(), bounds.exactCenterY())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click", e)
            false
        }
    }

    private fun performGestureClick(x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            
            dispatchGesture(gesture, null, null)
            Log.d(TAG, "Gesture click at ($x, $y)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gesture click failed", e)
            false
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "inDriver Bot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "inDriver Auto-Accept Bot notifications"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    fun getStats(): Triple<Int, Int, Int> {
        return Triple(totalOrdersDetected, ordersAccepted, ordersMissed)
    }

    fun resetStats() {
        totalOrdersDetected = 0
        ordersAccepted = 0
        ordersMissed = 0
    }
}
