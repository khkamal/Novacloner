package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode(val key: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    SYSTEM("system", "System");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class DefaultWorkspaceBehavior(val key: String, val label: String, val description: String) {
    OPEN_DETAILS(
        key = "open_details",
        label = "Open Workspace Details",
        description = "Tapping a workspace card opens its management and notes screen."
    ),
    LAUNCH_LINKED_APP(
        key = "launch_linked_app",
        label = "Launch Linked App Directly",
        description = "Tapping a workspace card launches its linked app if installed; otherwise opens details."
    );

    companion object {
        fun fromKey(key: String?): DefaultWorkspaceBehavior =
            entries.firstOrNull { it.key == key } ?: OPEN_DETAILS
    }
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultWorkspaceBehavior: DefaultWorkspaceBehavior = DefaultWorkspaceBehavior.OPEN_DETAILS,
    val isSecretPinConfigured: Boolean = false,
    val secretBiometricEnabled: Boolean = false,
    val secretAutoLockEnabled: Boolean = true,
)

data class DeviceInfoSnapshot(
    val androidVersion: String,
    val sdkLevel: Int,
    val manufacturer: String,
    val model: String,
    val deviceCode: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val packageName: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val localeDisplay: String,
    val timeZoneDisplay: String,
    val availableStorageBytes: Long?,
    val totalStorageBytes: Long?,
)

@Serializable
data class WorkspaceExportItem(
    val name: String,
    val generatedWorkspaceId: String? = null,
    val packageName: String? = null,
    val appDisplayName: String? = null,
    val description: String = "",
    val notes: String = "",
    val accentColorKey: String = "cobalt",
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class WorkspaceExportBundle(
    val formatVersion: Int = 1,
    val appName: String = "Nova Space",
    val exportedAt: Long,
    val workspaces: List<WorkspaceExportItem>,
)
