package com.example.openelsewhere

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val DEBUG_NOTIFICATION_CHANNEL_ID = "open_elsewhere_debug"
    }

    private var overlayManager: OverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPreferences

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (prefs.isPaused) {
            overlayManager?.dismiss()
            handler.removeCallbacks(usageStatsCheckRunnable)
        }
    }

    private val usageStatsCheckRunnable = object : Runnable {
        override fun run() {
            val manager = overlayManager ?: return
            if (!manager.isShowing) return
            val foreground = UsageStatsHelper.getForegroundPackage(this@BlockerAccessibilityService)
            if (foreground != null && !prefs.isWatched(foreground)) {
                manager.dismiss()
            } else {
                handler.postDelayed(this, 2_000L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppPreferences.getInstance(this)
        prefs.registerListener(preferenceListener)
        overlayManager = OverlayManager(this)
        val channel = NotificationChannel(
            "open_elsewhere_debug",
            "Debug — Activity Names",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (prefs.isPaused) {
            overlayManager?.dismiss()
            handler.removeCallbacks(usageStatsCheckRunnable)
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (prefs.isDebugMode && prefs.isWatched(packageName)) {
            postDebugNotification(packageName, className)
        }

        if (!prefs.isWatched(packageName)) {
            overlayManager?.dismiss()
            handler.removeCallbacks(usageStatsCheckRunnable)
            return
        }

        val allowedActivities =
            (WatchedApps.curatedAllowedActivities[packageName] ?: emptySet()) +
                prefs.getUnblockedActivities(packageName)

        val isBrowserActivity = className !in allowedActivities &&
            WatchedApps.BROWSER_KEYWORDS.any { keyword -> className.contains(keyword) }

        if (isBrowserActivity) {
            prefs.logBlockedActivity(packageName, className)
            overlayManager?.show()
            handler.removeCallbacks(usageStatsCheckRunnable)
            handler.postDelayed(usageStatsCheckRunnable, 2_000L)
        } else {
            overlayManager?.dismiss()
            handler.removeCallbacks(usageStatsCheckRunnable)
        }
    }

    override fun onInterrupt() {
        overlayManager?.dismiss()
        handler.removeCallbacks(usageStatsCheckRunnable)
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            prefs.unregisterListener(preferenceListener)
        }
        overlayManager?.dismiss()
        handler.removeCallbacks(usageStatsCheckRunnable)
        overlayManager = null
        super.onDestroy()
    }

    private fun postDebugNotification(packageName: String, className: String) {
        val packageShortName = packageName.substringAfterLast('.')
        val notification = NotificationCompat.Builder(this, DEBUG_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("[$packageShortName] → $className")
            .setContentText(className)
            .setSubText(packageName)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(className.hashCode(), notification)
    }
}
