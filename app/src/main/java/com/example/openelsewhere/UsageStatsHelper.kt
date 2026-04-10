package com.example.openelsewhere

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Secondary signal: uses UsageStatsManager to determine which package is currently
 * in the foreground. Used by [BlockerAccessibilityService] to confirm the user is
 * still inside a watched app when the overlay is active.
 *
 * Requires PACKAGE_USAGE_STATS permission (user must grant via Settings > Apps >
 * Special app access > Usage access).
 */
object UsageStatsHelper {

    /** Returns true if PACKAGE_USAGE_STATS has been granted by the user. */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns the package name of the most recently active app within the last 5 seconds,
     * or null if the permission is not granted or no data is available.
     */
    fun getForegroundPackage(context: Context): String? {
        if (!hasPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5_000L, now)
            ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
