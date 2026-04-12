package com.example.openelsewhere

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

class BlockerNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile var instance: BlockerNotificationListenerService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private var receiverRegistered = false

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.post { updateOverlayState() }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            registerReceiver(powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            receiverRegistered = true
        } catch (_: Exception) {}
        handler.post { updateOverlayState() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanup()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.post { dismissOverlay() }
        if (receiverRegistered) {
            try {
                unregisterReceiver(powerSaveReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
        instance = null
    }

    private fun updateOverlayState() {
        val pm = getSystemService(PowerManager::class.java)
        val prefs = AppPreferences.getInstance(this)
        val shouldShow = pm.isPowerSaveMode &&
            BlockerAccessibilityService.instance == null &&
            !prefs.isPaused &&
            prefs.getWatchedPackages().isNotEmpty() &&
            Settings.canDrawOverlays(this)
        if (shouldShow) showOverlay() else dismissOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_battery_saver, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_battery_saver_settings)
            .setOnClickListener {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (_: Exception) {}
    }

    private fun dismissOverlay() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
        overlayView = null
    }
}
