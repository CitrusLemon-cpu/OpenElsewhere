package com.example.openelsewhere

enum class BlockingMode {
    /** Block activities whose class name matches browser keywords (default). */
    KEYWORD,
    /** Block ALL activities NOT in the curated/user-allowed list. Immune to obfuscation. */
    ALLOWLIST
}
