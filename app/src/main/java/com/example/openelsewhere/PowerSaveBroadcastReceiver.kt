package com.example.openelsewhere

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils

/**
 * Manifest-declared receiver for power save mode changes, boot, and screen unlock.
 *
 * When UBS kills the app process, the dynamically-registered receiver inside
 * [BlockerNotificationListenerService] dies with it. This manifest receiver is woken by
 * Android (on OEM ROMs with autostart permission) and:
 * 1. Calls [NotificationListenerService.requestRebind] to reconnect the notification listener.
 * 2. Directly attempts to re-enable the accessibility service via [Settings.Secure] so
 *    Android's accessibility framework restarts [BlockerAccessibilityService].
 *
 * [Intent.ACTION_USER_PRESENT] fires when the user unlocks the screen after a UBS cycle —
 * a reliable catch-all for when UBS ends and the user resumes using the device.
 * [Intent.ACTION_BOOT_COMPLETED] ensures recovery after a reboot.
 */
class PowerSaveBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT -> {
                requestNlsRebind(context)
                tryReEnableAccessibilityService(context)
            }
        }
    }

    private fun requestNlsRebind(context: Context) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, BlockerNotificationListenerService::class.java)
            )
        } catch (_: Exception) {}
    }

    private fun tryReEnableAccessibilityService(context: Context) {
        if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val cn = ComponentName(context, BlockerAccessibilityService::class.java)
                .flattenToString()
            val cr = context.contentResolver
            val current = Settings.Secure.getString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(current)
            val existing = mutableListOf<String>()
            while (splitter.hasNext()) existing.add(splitter.next())
            if (existing.none { it.equals(cn, ignoreCase = true) }) {
                Settings.Secure.putString(
                    cr,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (existing + cn).joinToString(":")
                )
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: Exception) {}
    }
}
