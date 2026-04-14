package com.example.openelsewhere

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ForegroundPollingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "open_elsewhere_polling"
        private const val POLL_INTERVAL_MS = 500L

        @Volatile
        var instance: ForegroundPollingService? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ForegroundPollingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundPollingService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPreferences
    private lateinit var overlayManager: OverlayManager

    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (prefs.isPaused) overlayManager.dismiss()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = AppPreferences.getInstance(this)
        prefs.registerListener(preferenceListener)
        overlayManager = OverlayManager(this)
        startForegroundWithNotification()
        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(pollRunnable)
        overlayManager.dismiss()
        prefs.unregisterListener(preferenceListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    private fun poll() {
        if (BlockerAccessibilityService.instance != null) {
            overlayManager.dismiss()
            return
        }

        if (prefs.isPaused) {
            overlayManager.dismiss()
            return
        }

        val foregroundPkg = UsageStatsHelper.getForegroundPackageViaEvents(this)
            ?: return

        if (prefs.isWatched(foregroundPkg)) {
            if (!overlayManager.isShowing) {
                overlayManager.show()
            }
        } else {
            overlayManager.dismiss()
        }
    }

    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenElsewhere Protection",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.polling_service_notification_title))
            .setContentText(getString(R.string.polling_service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
