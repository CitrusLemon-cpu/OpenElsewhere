package com.example.openelsewhere

import android.content.Context
import android.content.SharedPreferences

class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "open_elsewhere_prefs"
        private const val KEY_WATCHED = "watched_packages"
        private const val KEY_PAUSED = "paused"
        private const val KEY_DEBUG_MODE = "debug_mode"
        private const val PREFIX_UNBLOCKED = "unblocked_"
        private const val PREFIX_BLOCKED_LOG = "blocked_log_"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
    }

    var isPaused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PAUSED, value).apply()
        }

    var isDebugMode: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEBUG_MODE, value).apply()
        }

    fun getWatchedPackages(): Set<String> =
        prefs.getStringSet(KEY_WATCHED, WatchedApps.defaultWatchedPackages)
            ?: WatchedApps.defaultWatchedPackages

    fun isWatched(packageName: String): Boolean = packageName in getWatchedPackages()

    fun setWatched(packageName: String, watched: Boolean) {
        val current = getWatchedPackages().toMutableSet()
        if (watched) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet(KEY_WATCHED, current).apply()
    }

    fun getUnblockedActivities(packageName: String): Set<String> =
        prefs.getStringSet("$PREFIX_UNBLOCKED$packageName", emptySet()) ?: emptySet()

    fun addUnblockedActivity(packageName: String, activityClass: String) {
        val current = getUnblockedActivities(packageName).toMutableSet()
        current.add(activityClass)
        prefs.edit().putStringSet("$PREFIX_UNBLOCKED$packageName", current).apply()
    }

    fun removeUnblockedActivity(packageName: String, activityClass: String) {
        val current = getUnblockedActivities(packageName).toMutableSet()
        current.remove(activityClass)
        prefs.edit().putStringSet("$PREFIX_UNBLOCKED$packageName", current).apply()
    }

    fun logBlockedActivity(packageName: String, activityClass: String) {
        val current = getBlockedActivityLog(packageName).toMutableSet()
        current.add(activityClass)
        prefs.edit().putStringSet("$PREFIX_BLOCKED_LOG$packageName", current).apply()
    }

    fun getBlockedActivityLog(packageName: String): Set<String> =
        prefs.getStringSet("$PREFIX_BLOCKED_LOG$packageName", emptySet()) ?: emptySet()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
