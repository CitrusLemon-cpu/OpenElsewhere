package com.example.openelsewhere

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchService: SwitchMaterial
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvUsageStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        switchService = findViewById(R.id.switch_service)
        tvServiceStatus = findViewById(R.id.tv_service_status)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvUsageStatus = findViewById(R.id.tv_usage_status)

        findViewById<MaterialButton>(R.id.btn_accessibility_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.btn_overlay_settings).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<MaterialButton>(R.id.btn_usage_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    private fun updateStatusUI() {
        val serviceEnabled = isAccessibilityServiceEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)
        val usageGranted = UsageStatsHelper.hasPermission(this)

        // Status card switch
        switchService.isChecked = serviceEnabled
        tvServiceStatus.text = if (serviceEnabled)
            getString(R.string.status_active) else getString(R.string.status_inactive)

        // Accessibility card
        tvAccessibilityStatus.text = if (serviceEnabled)
            getString(R.string.status_active) else getString(R.string.status_inactive)
        tvAccessibilityStatus.setTextColor(
            if (serviceEnabled) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_light)
        )

        // Overlay card
        tvOverlayStatus.text = if (overlayGranted)
            getString(R.string.status_granted) else getString(R.string.status_not_granted)
        tvOverlayStatus.setTextColor(
            if (overlayGranted) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_light)
        )

        // Usage access card
        tvUsageStatus.text = if (usageGranted)
            getString(R.string.status_granted) else getString(R.string.status_not_granted)
        tvUsageStatus.setTextColor(
            if (usageGranted) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_orange_light)  // orange = optional, not an error
        )
    }

    /**
     * Checks whether [BlockerAccessibilityService] is listed in
     * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, BlockerAccessibilityService::class.java)
        val setting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val flat = splitter.next()
            val cn = ComponentName.unflattenFromString(flat)
            if (cn != null && cn == expected) return true
        }
        return false
    }
}
