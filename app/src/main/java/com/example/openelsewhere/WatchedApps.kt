package com.example.openelsewhere

object WatchedApps {
    val BROWSER_KEYWORDS = listOf("WebView", "Browser", "H5", "Web", "Url")

    val defaultWatchedPackages: Set<String> = setOf("com.tencent.mm")

    val curatedAllowedActivities: Map<String, Set<String>> = mapOf(
        "com.tencent.mm" to setOf(
            "com.tencent.mm.ui.LauncherUI",
            "com.tencent.mm.plugin.account.ui.WelcomeActivity",
            "com.tencent.mm.plugin.account.ui.PhoneLoginUI",
            "com.tencent.mm.ui.contact.AddContactUI",
            "com.tencent.mm.ui.tools.PayUI"
        )
    )
}
