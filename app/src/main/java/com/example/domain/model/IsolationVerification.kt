package com.example.domain.model

enum class IsolationVerificationStatus(
    val displayLabel: String,
    val isVerifiedIsolated: Boolean,
) {
    VERIFIED_WORK_PROFILE(
        displayLabel = "Verified — OS Work Profile Isolation",
        isVerifiedIsolated = true,
    ),
    HOST_SHARED_DATA(
        displayLabel = "Not Isolated — Shared Host Data",
        isVerifiedIsolated = false,
    ),
    NO_SECONDARY_PROFILE(
        displayLabel = "Unverified — No Secondary Profile Found",
        isVerifiedIsolated = false,
    ),
    PROFILE_ACCESS_RESTRICTED(
        displayLabel = "Unverified — Profile Access Restricted",
        isVerifiedIsolated = false,
    ),
    APP_NOT_INSTALLED_IN_PROFILE(
        displayLabel = "Unverified — App Missing in Target Profile",
        isVerifiedIsolated = false,
    ),
    CROSS_PROFILE_LAUNCH_RESTRICTED(
        displayLabel = "Unverified — Cross-Profile Launch Restricted",
        isVerifiedIsolated = false,
    ),
    UNSUPPORTED_CONTAINER_ENGINE(
        displayLabel = "Unsupported — No Container Engine",
        isVerifiedIsolated = false,
    ),
}

enum class ProfileLaunchPath(val displayLabel: String) {
    HOST_PACKAGE_MANAGER("Host PackageManager (Current User)"),
    CROSS_PROFILE_APPS("CrossProfileApps (Same-Package OS Profile)"),
    LAUNCHER_APPS("LauncherApps (Authorized Shortcut Host)"),
    UNAVAILABLE("Blocked / Not Permitted"),
}

data class IsolationVerificationReport(
    val status: IsolationVerificationStatus,
    val launchPath: ProfileLaunchPath,
    val currentProfileLabel: String,
    val targetProfileLabel: String? = null,
    val targetProfileSerial: Long? = null,
    val targetAppInstalledInProfile: Boolean = false,
    val launchPermittedInProfile: Boolean = false,
    val profileHasOwnAppData: Boolean = false,
    val isGenuineIsolationVerified: Boolean = false,
    val verificationSummary: String,
    val guidedSetupSteps: List<String> = emptyList(),
)
