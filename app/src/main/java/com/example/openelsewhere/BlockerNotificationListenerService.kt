package com.example.openelsewhere

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils
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
    private var periodicCheckScheduled = false

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.post { updateOverlayState() }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { updateOverlayState() }
    }

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            periodicCheckScheduled = false
            updateOverlayState()
            if (overlayView != null) {
                startPeriodicCheckIfNeeded()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AppPreferences.getInstance(this).registerListener(prefsListener)
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
        handler.removeCallbacks(periodicCheckRunnable)
        periodicCheckScheduled = false
        handler.post { dismissOverlay() }
        try {
            AppPreferences.getInstance(this).unregisterListener(prefsListener)
        } catch (_: Exception) {}
        if (receiverRegistered) {
            try {
                unregisterReceiver(powerSaveReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
        instance = null
    }

    private fun updateOverlayState() {
        val prefs = AppPreferences.getInstance(this)
        val wasShowing = overlayView != null
        val shouldShow = isAccessibilityServiceEnabled() &&
            BlockerAccessibilityService.instance == null &&
            !prefs.isPaused &&
            prefs.getWatchedPackages().isNotEmpty() &&
            Settings.canDrawOverlays(this)
        if (shouldShow) {
            showOverlay()
            if (!wasShowing) {
                startPeriodicCheckIfNeeded()
            }
        } else {
            dismissOverlay()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, BlockerAccessibilityService::class.java)
        val setting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(splitter.next())
            if (cn != null && cn == expected) return true
        }
        return false
    }

    private fun startPeriodicCheckIfNeeded() {
        if (periodicCheckScheduled) return
        periodicCheckScheduled = true
        handler.postDelayed(periodicCheckRunnable, 3_000L)
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
