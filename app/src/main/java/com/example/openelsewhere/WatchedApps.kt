package com.example.openelsewhere

/**
 * Hardcoded registry of watched apps and their "safe" (non-browser) activity class names.
 *
 * Logic: an activity in a watched package is considered an in-app browser if:
 *   1. Its fully-qualified class name is NOT in [AppConfig.allowedActivities], AND
 *   2. Its class name contains at least one of [BROWSER_KEYWORDS] (case-sensitive)
 *
 * To add more apps: add another entry to [configs].
 * To expand allowed activities for WeChat: add to the set below.
 */

data class AppConfig(
    val packageName: String,
    /** Fully-qualified Activity class names that are safe (normal app UI, not browsers). */
    val allowedActivities: Set<String>
)

object WatchedApps {

    /** Keywords that indicate an in-app browser Activity class name. Case-sensitive. */
    val BROWSER_KEYWORDS = listOf("WebView", "Browser", "H5", "Web", "Url")

    val configs: Map<String, AppConfig> = mapOf(
        "com.tencent.mm" to AppConfig(
            packageName = "com.tencent.mm",
            allowedActivities = setOf(
                "com.tencent.mm.ui.LauncherUI",                    // Home / conversations list
                "com.tencent.mm.plugin.account.ui.WelcomeActivity",// Welcome / login
                "com.tencent.mm.plugin.account.ui.PhoneLoginUI",   // Phone login
                "com.tencent.mm.ui.contact.AddContactUI",          // Add contact
                "com.tencent.mm.ui.tools.PayUI",                   // WeChat Pay hub
            )
        )
    )
}
