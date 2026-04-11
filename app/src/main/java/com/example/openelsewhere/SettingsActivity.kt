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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

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
        updateStatuses()
    }

    private fun updateStatuses() {
        val serviceEnabled = isAccessibilityServiceEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)
        val usageGranted = UsageStatsHelper.hasPermission(this)

        setStatus(
            R.id.tv_accessibility_status,
            serviceEnabled,
            R.string.status_active,
            R.string.status_inactive,
            required = true
        )
        setStatus(
            R.id.tv_overlay_status,
            overlayGranted,
            R.string.status_granted,
            R.string.status_not_granted,
            required = true
        )
        setStatus(
            R.id.tv_usage_status,
            usageGranted,
            R.string.status_granted,
            R.string.status_not_granted,
            required = false
        )
    }

    private fun setStatus(
        tvId: Int,
        granted: Boolean,
        grantedRes: Int,
        notGrantedRes: Int,
        required: Boolean
    ) {
        val tv = findViewById<TextView>(tvId)
        tv.text = getString(if (granted) grantedRes else notGrantedRes)
        tv.setTextColor(
            when {
                granted -> getColor(android.R.color.holo_green_dark)
                required -> getColor(android.R.color.holo_red_light)
                else -> getColor(android.R.color.holo_orange_light)
            }
        )
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
}
