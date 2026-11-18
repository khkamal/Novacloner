package com.example.domain.model

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
)

enum class AppFilterMode(val label: String) {
    ALL("All Launchable"),
    USER("User Apps"),
    SYSTEM("System Apps")
}
