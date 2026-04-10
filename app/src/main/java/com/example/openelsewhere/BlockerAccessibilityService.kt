package com.example.openelsewhere

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Core service that monitors TYPE_WINDOW_STATE_CHANGED events.
 *
 * Detection logic:
 *   - Event package is in [WatchedApps.configs]
 *   - Event className is NOT in the app's [AppConfig.allowedActivities]
 *   - Event className contains at least one of [WatchedApps.BROWSER_KEYWORDS]
 *   → show overlay
 *
 *   - Event package is in watched apps but className IS in allowedActivities,
 *     OR className does not contain any browser keyword
 *   → dismiss overlay
 *
 *   - Event package is NOT in watched apps
 *   → dismiss overlay (user left all watched apps)
 *
 * UsageStats secondary signal: when the overlay is active, a periodic check via
 * [UsageStatsHelper] confirms the user is still in a watched app. If UsageStats
 * reports the foreground package is no longer watched, the overlay is dismissed.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private var overlayManager: OverlayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Periodic UsageStats check — runs every 2s while overlay is visible. */
    private val usageStatsCheckRunnable = object : Runnable {
        override fun run() {
            val manager = overlayManager ?: return
            if (!manager.isShowing) return

            val foreground = UsageStatsHelper.getForegroundPackage(this@BlockerAccessibilityService)
            if (foreground != null && !WatchedApps.configs.containsKey(foreground)) {
                // Secondary signal: user navigated away from all watched apps
                manager.dismiss()
            } else {
                // Keep polling while overlay is showing
                handler.postDelayed(this, 2_000L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)

        // Programmatically confirm event type filter (belt-and-suspenders with XML config)
        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        val config = WatchedApps.configs[packageName]
        if (config == null) {
            // Not a watched app — dismiss overlay if it's showing
            overlayManager?.dismiss()
            handler.removeCallbacks(usageStatsCheckRunnable)
            return
        }

        val isBrowserActivity = className !in config.allowedActivities &&
                WatchedApps.BROWSER_KEYWORDS.any { keyword -> className.contains(keyword) }

        if (isBrowserActivity) {
            overlayManager?.show()
            // Start secondary UsageStats check loop
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
        overlayManager?.dismiss()
        handler.removeCallbacks(usageStatsCheckRunnable)
        overlayManager = null
        super.onDestroy()
    }
}
