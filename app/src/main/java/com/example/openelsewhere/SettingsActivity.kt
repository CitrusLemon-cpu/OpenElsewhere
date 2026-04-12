package com.example.openelsewhere

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        prefs = AppPreferences.getInstance(this)

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
        findViewById<MaterialButton>(R.id.btn_shortcut_settings).setOnClickListener {
            val componentName = ComponentName(this, BlockerAccessibilityService::class.java)
                .flattenToString()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                val args = Bundle()
                args.putString(":settings:fragment_args_key", componentName)
                putExtra(":settings:show_fragment_args", args)
                putExtra(":settings:fragment_args_key", componentName)
            }
            startActivity(intent)
        }
        findViewById<MaterialButton>(R.id.btn_battery_opt).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
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
        findViewById<TextView>(R.id.tv_protected_apps_desc).text = getOemDescriptionString()
        findViewById<MaterialButton>(R.id.btn_protected_apps).setOnClickListener {
            val oemIntent = getOemProtectedAppsIntent()
            if (oemIntent != null) {
                startActivity(oemIntent)
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        findViewById<SwitchMaterial>(R.id.switch_debug_mode).apply {
            isChecked = prefs.isDebugMode
            setOnCheckedChangeListener { _, isChecked ->
                prefs.isDebugMode = isChecked
            }
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
        val shortcutEnabled = isAccessibilityShortcutEnabled()
        setStatus(
            R.id.tv_shortcut_status,
            shortcutEnabled,
            R.string.status_active,
            R.string.status_inactive,
            required = false
        )
        val batteryExempted = isBatteryOptimizationExempted()
        setStatus(
            R.id.tv_battery_opt_status,
            batteryExempted,
            R.string.status_exempted,
            R.string.status_not_exempted,
            required = false
        )
    }

    private fun isBatteryOptimizationExempted(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun getOemProtectedAppsIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                Intent().setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                Intent().setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> listOf(
                Intent().setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.privacypermissionsentry.PermissionTopActivity"
                ),
                Intent().setClassName(
                    "com.oppo.safe",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            )
            manufacturer.contains("vivo") -> listOf(
                Intent().setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            )
            manufacturer.contains("samsung") -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
            else -> emptyList()
        }
        return intents.firstOrNull { intent ->
            intent.component?.let { cn ->
                packageManager.resolveActivity(
                    Intent().setClassName(cn.packageName, cn.className),
                    0
                ) != null
            } ?: (packageManager.resolveActivity(intent, 0) != null)
        }
    }

    private fun getOemDescriptionString(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                getString(R.string.desc_protected_apps_huawei)
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                getString(R.string.desc_protected_apps_xiaomi)
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
                getString(R.string.desc_protected_apps_oppo)
            manufacturer.contains("vivo") ->
                getString(R.string.desc_protected_apps_vivo)
            manufacturer.contains("samsung") ->
                getString(R.string.desc_protected_apps_samsung)
            else ->
                getString(R.string.desc_protected_apps_generic)
        }
    }

    private fun isAccessibilityShortcutEnabled(): Boolean {
        val ourComponent = ComponentName(this, BlockerAccessibilityService::class.java)
            .flattenToString().lowercase()

        fun String?.containsOurService(): Boolean {
            if (isNullOrBlank()) return false
            return split(":").any { it.trim().lowercase() == ourComponent }
        }

        val shortcutTarget = Settings.Secure.getString(
            contentResolver,
            "accessibility_shortcut_target_service"
        )
        val buttonTargets = Settings.Secure.getString(
            contentResolver,
            "accessibility_button_targets"
        )
        return shortcutTarget.containsOurService() || buttonTargets.containsOurService()
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
