package com.example.openelsewhere

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

private const val PERSISTENT_CHANNEL_ID = "open_elsewhere_persistent"
private const val PERSISTENT_NOTIF_ID = 1001

class BlockerNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile var instance: BlockerNotificationListenerService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var hardcoreOverlayView: View? = null
    private var floatingBubbleView: View? = null
    private var floatingBubbleParams: WindowManager.LayoutParams? = null
    private var bubbleDragStartX = 0f
    private var bubbleDragStartY = 0f
    private var bubbleInitialX = 0
    private var bubbleInitialY = 0
    private lateinit var windowManager: WindowManager
    private var receiverRegistered = false
    private var accessibilityObserverRegistered = false
    private var periodicCheckScheduled = false
    private var lastKnownPowerSaveState = false

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
        val persistentChannel = NotificationChannel(
            PERSISTENT_CHANNEL_ID,
            "Persistent Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(persistentChannel)
        AppPreferences.getInstance(this).registerListener(prefsListener)
        try {
            registerReceiver(powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            receiverRegistered = true
        } catch (_: Exception) {}
        if (!accessibilityObserverRegistered) {
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
        }
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
        dismissPersistentNotification()
        dismissFloatingBubble()
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
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val powerSaveJustEnded = lastKnownPowerSaveState && !pm.isPowerSaveMode
                lastKnownPowerSaveState = pm.isPowerSaveMode
                showOverlay()
                if (!wasUbsShowing || powerSaveJustEnded) {
                    tryReEnableAccessibilityService()
                }
                if (!wasUbsShowing) {
                    navigateToHomeScreen()
                    startPeriodicCheckIfNeeded()
                }
            } else {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                lastKnownPowerSaveState = pm.isPowerSaveMode
                dismissOverlay()
            }
        }
        updatePersistentNotification()
        updateBubble()
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

    private fun updatePersistentNotification() {
        val prefs = AppPreferences.getInstance(this)
        if (!prefs.isPersistentNotificationEnabled) {
            dismissPersistentNotification()
            return
        }
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, text) = when {
            prefs.isPaused -> "OpenElsewhere paused" to "Tap to manage settings"
            BlockerAccessibilityService.instance != null ->
                "OpenElsewhere active" to "Monitoring ${prefs.getWatchedPackages().size} apps"
            else -> "⚠️ Monitoring disabled" to "Accessibility service is off — tap to fix"
        }
        val notification = NotificationCompat.Builder(this, PERSISTENT_CHANNEL_ID)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        getSystemService(NotificationManager::class.java).notify(PERSISTENT_NOTIF_ID, notification)
    }

    private fun dismissPersistentNotification() {
        getSystemService(NotificationManager::class.java).cancel(PERSISTENT_NOTIF_ID)
    }

    private fun updateBubble() {
        val prefs = AppPreferences.getInstance(this)
        if (prefs.isFloatingBubbleEnabled && Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        } else {
            dismissFloatingBubble()
        }
    }

    private fun showFloatingBubble() {
        if (floatingBubbleView != null) return
        val prefs = AppPreferences.getInstance(this)
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_floating_bubble, null)
        view.alpha = 0.85f

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.floatingBubbleX.let { if (it == -1) 900 else it }
            y = prefs.floatingBubbleY
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleDragStartX = event.rawX
                    bubbleDragStartY = event.rawY
                    bubbleInitialX = params.x
                    bubbleInitialY = params.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - bubbleDragStartX).toInt()
                    val deltaY = (event.rawY - bubbleDragStartY).toInt()
                    params.x = bubbleInitialX + deltaX
                    params.y = bubbleInitialY + deltaY
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Exception) {}
                }

                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - bubbleDragStartX) < 15f && abs(event.rawY - bubbleDragStartY) < 15f) {
                        val intent = Intent(this, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    prefs.floatingBubbleX = params.x
                    prefs.floatingBubbleY = params.y
                }
            }
            true
        }

        try {
            windowManager.addView(view, params)
            floatingBubbleView = view
            floatingBubbleParams = params
        } catch (_: Exception) {}
    }

    private fun dismissFloatingBubble() {
        val view = floatingBubbleView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
        floatingBubbleView = null
        floatingBubbleParams = null
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
