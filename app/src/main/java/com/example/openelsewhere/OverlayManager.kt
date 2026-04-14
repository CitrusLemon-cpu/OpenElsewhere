package com.example.openelsewhere

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button

class OverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    val isShowing: Boolean get() = overlayView != null

    fun show() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(context)) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
        }

        view.findViewById<Button>(R.id.btn_go_back).setOnClickListener {
            val a11y = BlockerAccessibilityService.instance
            if (a11y != null) {
                a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            dismiss()
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    fun dismiss() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        overlayView = null
    }
}
