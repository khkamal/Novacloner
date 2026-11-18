package com.example.domain.model

enum class ExecutionMode(
    val key: String,
    val displayLabel: String,
    val shortBadge: String,
) {
    HOST_DIRECT(
        key = "host_direct",
        displayLabel = "Host App — Shared Data",
        shortBadge = "Host App",
    ),
    WORK_PROFILE(
        key = "work_profile",
        displayLabel = "Work Profile (Read-Only Detection)",
        shortBadge = "Work Profile",
    ),
    ISOLATED_CONTAINER(
        key = "isolated_container",
        displayLabel = "Isolated Container",
        shortBadge = "Container",
    ),
    UNSUPPORTED(
        key = "unsupported",
        displayLabel = "Unsupported",
        shortBadge = "Unsupported",
    );

    companion object {
        fun fromKey(key: String?): ExecutionMode {
            val normalized = key?.trim().orEmpty()
            return entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) } ?: HOST_DIRECT
        }
    }
}

data class WorkProfileProbeResult(
    val totalAccessibleProfiles: Int = 1,
    val currentProfileSerial: Long = 0L,
    val secondaryProfileSerials: List<Long> = emptyList(),
    val selectedTargetSerial: Long? = null,
    val secondaryProfileDetected: Boolean = false,
    val secondaryProfileQueryAccessible: Boolean = false,
    val targetAppVisibleInSecondaryProfile: Boolean = false,
    val selfInstalledInSecondaryProfile: Boolean = false,
    val crossProfileApiAvailable: Boolean = false,
    val crossProfileTargetCount: Int = 0,
    val crossProfileCanInteract: Boolean = false,
    val crossProfileCanRequestPermission: Boolean = false,
    val hasLauncherShortcutPermission: Boolean = false,
    val canLaunchExternalAppAcrossProfiles: Boolean = false,
    val capabilitySummary: String = "Only the primary personal profile is accessible.",
    val limitationNote: String = "Work Profile provisioning cannot be triggered automatically by a non-DPC app.",
)

enum class CompatibilityStatus(val label: String) {
    HOST_LAUNCHABLE("Launchable on Host"),
    LIMITED("Host-Only / Restricted"),
    UNAVAILABLE("Not Installed or Unlaunchable"),
}

data class AppCompatibilityReport(
    val packageName: String,
    val status: CompatibilityStatus,
    val isInstalled: Boolean,
    val hasLaunchableActivity: Boolean,
    val isSystemApp: Boolean = false,
    val isSplitApk: Boolean = false,
    val hasNativeLibraries: Boolean = false,
    val sharedUserId: String? = null,
    val targetSdkVersion: Int = 0,
    val minSdkVersion: Int = 0,
    val referencesPlayServices: Boolean = false,
    val warnings: List<String> = emptyList(),
)
