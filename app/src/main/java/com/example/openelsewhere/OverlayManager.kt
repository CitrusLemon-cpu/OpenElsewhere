package com.example.openelsewhere

import android.content.Context
import android.graphics.PixelFormat
import android.accessibilityservice.AccessibilityService
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button

/**
 * Manages a full-screen [WindowManager] accessibility overlay. Must be called
 * on the main thread.
 *
 * The overlay shows "In-app browser blocked" and a "Go Back" button that
 * calls [BlockerAccessibilityService.performGlobalAction].
 */
class OverlayManager(private val service: BlockerAccessibilityService) {

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    val isShowing: Boolean get() = overlayView != null

    /** Show the block overlay. No-op if already showing. */
    fun show() {
        if (overlayView != null) return

        val view = LayoutInflater.from(service).inflate(R.layout.overlay_block, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
        }

        view.findViewById<Button>(R.id.btn_go_back).setOnClickListener {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    /** Remove the block overlay. No-op if not currently showing. */
    fun dismiss() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // View already detached; safe to ignore
        }
        overlayView = null
    }
}
