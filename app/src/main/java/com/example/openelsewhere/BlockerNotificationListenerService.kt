package com.example.openelsewhere

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
    private var hardcoreOverlayView: View? = null
    private lateinit var windowManager: WindowManager
    private var receiverRegistered = false
    private var accessibilityObserverRegistered = false
    private var periodicCheckScheduled = false

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.post { updateOverlayState() }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { updateOverlayState() }
    }

    private val accessibilitySettingsObserver = object : android.database.ContentObserver(
        Handler(Looper.getMainLooper())
    ) {
        override fun onChange(selfChange: Boolean) {
            handler.post { updateOverlayState() }
        }
    }

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            periodicCheckScheduled = false
            updateOverlayState()
            if (overlayView != null || hardcoreOverlayView != null) {
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
        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                accessibilitySettingsObserver
            )
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                false,
                accessibilitySettingsObserver
            )
            accessibilityObserverRegistered = true
        } catch (_: Exception) {}
        handler.postDelayed({ updateOverlayState() }, 2_000L)
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
        handler.post { dismissHardcoreOverlay() }
        try {
            AppPreferences.getInstance(this).unregisterListener(prefsListener)
        } catch (_: Exception) {}
        if (receiverRegistered) {
            try {
                unregisterReceiver(powerSaveReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
        if (accessibilityObserverRegistered) {
            try {
                contentResolver.unregisterContentObserver(accessibilitySettingsObserver)
            } catch (_: Exception) {}
            accessibilityObserverRegistered = false
        }
        instance = null
    }

    private fun isAccessibilityServiceEnabledInSettings(): Boolean {
        val globalEnabled = Settings.Secure.getInt(
            contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
        ) == 1
        if (!globalEnabled) return false
        val expected = ComponentName(this, BlockerAccessibilityService::class.java)
        val setting = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(splitter.next())
            if (cn != null && cn == expected) return true
        }
        return false
    }

    private fun updateOverlayState() {
        val prefs = AppPreferences.getInstance(this)

        val hardcoreActive = prefs.isHardcoreMode &&
            !prefs.isPaused &&
            !isAccessibilityServiceEnabledInSettings() &&
            Settings.canDrawOverlays(this)

        val wasUbsShowing = overlayView != null
        val ubsActive = !hardcoreActive &&
            BlockerAccessibilityService.instance == null &&
            !prefs.isPaused &&
            prefs.getWatchedPackages().isNotEmpty() &&
            Settings.canDrawOverlays(this) &&
            prefs.isUltraBatterySaverScreenEnabled

        if (hardcoreActive) {
            val wasHardcoreShowing = hardcoreOverlayView != null
            showHardcoreOverlay()
            dismissOverlay()
            if (!wasHardcoreShowing) {
                tryReEnableAccessibilityService()
                navigateToHomeScreen()
                startPeriodicCheckIfNeeded()
            }
        } else {
            dismissHardcoreOverlay()
            if (ubsActive) {
                showOverlay()
                if (!wasUbsShowing) {
                    tryReEnableAccessibilityService()
                    navigateToHomeScreen()
                    startPeriodicCheckIfNeeded()
                }
            } else {
                dismissOverlay()
            }
        }
    }

    private fun tryReEnableAccessibilityService() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return
        try {
            val cn = ComponentName(this, BlockerAccessibilityService::class.java).flattenToString()
            val current = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val parts = if (current.isBlank()) emptyList() else current.split(":")
            if (parts.none { it.equals(cn, ignoreCase = true) }) {
                val updated = (parts + cn).joinToString(":")
                Settings.Secure.putString(
                    contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated
                )
            }
            Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: Exception) {}
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    private fun showHardcoreOverlay() {
        if (hardcoreOverlayView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_hardcore, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_hardcore_settings)
            .setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

        try {
            windowManager.addView(view, params)
            hardcoreOverlayView = view
        } catch (_: Exception) {}
    }

    private fun navigateToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun dismissOverlay() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
        overlayView = null
    }

    private fun dismissHardcoreOverlay() {
        val view = hardcoreOverlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
        hardcoreOverlayView = null
    }
}
