package com.example.openelsewhere

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/**
 * Manifest-declared receiver for [android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED].
 *
 * When Ultra Battery Saver kills the app process the dynamically-registered receiver in
 * [BlockerNotificationListenerService] dies with it.  This manifest receiver is woken by
 * Android (on OEM ROMs with "autostart" permission granted) and calls
 * [NotificationListenerService.requestRebind] so the system reconnects the listener.
 * Once reconnected, [BlockerNotificationListenerService.onListenerConnected] fires and
 * [BlockerNotificationListenerService.updateOverlayState] shows the UBS blocking screen.
 *
 * Also fires when power-save mode ends, which is a no-op because requestRebind is a
 * no-op when the listener is already connected.
 */
class PowerSaveBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, BlockerNotificationListenerService::class.java)
            )
        } catch (_: Exception) {}
    }
}
