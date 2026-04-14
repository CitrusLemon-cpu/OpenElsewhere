package com.example.openelsewhere

import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class BlockerNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: BlockerNotificationListenerService? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // NLS survives ultra battery saver on MIUI — use its connection to ensure
        // ForegroundPollingService is running (handles cases where the OS killed it).
        if (UsageStatsHelper.hasPermission(this) && Settings.canDrawOverlays(this)) {
            ForegroundPollingService.start(this)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    // Pure keepalive — no notification interception or modification
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
