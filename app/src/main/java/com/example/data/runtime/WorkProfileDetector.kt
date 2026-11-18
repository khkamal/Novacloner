package com.example.data.runtime

import android.content.ComponentName
import android.content.Context
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.example.MainActivity
import com.example.domain.model.IsolationVerificationReport
import com.example.domain.model.IsolationVerificationStatus
import com.example.domain.model.ProfileLaunchPath
import com.example.domain.model.WorkProfileProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inspects accessible Android user profiles using official, non-privileged Android SDK APIs
 * and verifies whether genuine Work Profile isolation and launch capability exist for a package.
 */
class WorkProfileDetector(
    private val context: Context,
    private val backend: ProfileInspectionBackend = AndroidProfileInspectionBackend(context),
) {

    interface ProfileInspectionBackend {
        fun myUserHandle(): UserHandle
        fun getUserProfiles(): List<UserHandle>
        fun getUserSerialNumber(userHandle: UserHandle): Long
        fun getCrossProfileTargets(): List<UserHandle>
        fun isCrossProfileApiSupported(): Boolean
        fun canInteractAcrossProfiles(): Boolean
        fun canRequestInteractAcrossProfiles(): Boolean
        fun hasShortcutHostPermission(): Boolean
        fun canQueryActivitiesInProfile(userHandle: UserHandle, packageName: String?): ProfileQueryOutcome
    }

    sealed interface ProfileQueryOutcome {
        data class Accessible(
            val targetPackagePresent: Boolean,
            val resolvedComponent: ComponentName? = null,
        ) : ProfileQueryOutcome

        data class Restricted(val reason: String) : ProfileQueryOutcome
    }

    data class VerifiedProfileTarget(
        val userHandle: UserHandle,
        val userSerial: Long,
        val launchPath: ProfileLaunchPath,
        val componentName: ComponentName,
    )

    data class DetailedProfileEvaluation(
        val probe: WorkProfileProbeResult,
        val verification: IsolationVerificationReport,
        val verifiedTarget: VerifiedProfileTarget? = null,
    )

    suspend fun inspect(packageName: String? = null): WorkProfileProbeResult =
        evaluate(packageName).probe

    suspend fun verifyWorkProfileIsolation(packageName: String? = null): DetailedProfileEvaluation =
        evaluate(packageName)

    private suspend fun evaluate(packageName: String?): DetailedProfileEvaluation =
        withContext(Dispatchers.IO) {
            val targetPkg = packageName?.trim()?.takeIf { it.isNotEmpty() }
            val currentHandle = runCatching { backend.myUserHandle() }.getOrNull()
            val currentSerial = currentHandle?.let {
                runCatching { backend.getUserSerialNumber(it) }.getOrDefault(0L)
            } ?: 0L

            val profiles = runCatching { backend.getUserProfiles() }.getOrDefault(emptyList())
            val totalProfiles = profiles.size.coerceAtLeast(1)

            val secondaryProfiles = if (currentHandle != null) {
                profiles.filter { it != currentHandle }
            } else {
                emptyList()
            }

            val crossProfileSupported = backend.isCrossProfileApiSupported()
            val crossProfileTargets = if (crossProfileSupported) {
                runCatching { backend.getCrossProfileTargets() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val allSecondaryHandles = (secondaryProfiles + crossProfileTargets).distinct()
            val secondarySerials = allSecondaryHandles.map { handle ->
                runCatching { backend.getUserSerialNumber(handle) }.getOrDefault(-1L)
            }

            val crossProfileCanInteract = runCatching { backend.canInteractAcrossProfiles() }.getOrDefault(false)
            val crossProfileCanRequest = runCatching { backend.canRequestInteractAcrossProfiles() }.getOrDefault(false)
            val hasLauncherPermission = runCatching { backend.hasShortcutHostPermission() }.getOrDefault(false)
            val selfInSecondary = crossProfileTargets.isNotEmpty()

            var anySecondaryQueryable = false
            var targetVisibleInSecondary = false
            var resolvedExternalTarget: Pair<UserHandle, ComponentName>? = null
            var queryRestrictionNote: String? = null

            for (profile in allSecondaryHandles) {
                when (val outcome = backend.canQueryActivitiesInProfile(profile, targetPkg)) {
                    is ProfileQueryOutcome.Accessible -> {
                        anySecondaryQueryable = true
                        if (outcome.targetPackagePresent) {
                            targetVisibleInSecondary = true
                            val comp = outcome.resolvedComponent
                            if (resolvedExternalTarget == null && comp != null) {
                                resolvedExternalTarget = profile to comp
                            }
                        }
                    }
                    is ProfileQueryOutcome.Restricted -> {
                        if (queryRestrictionNote == null) {
                            queryRestrictionNote = outcome.reason
                        }
                    }
                }
            }

            val isSelfPackage = targetPkg != null && targetPkg == context.packageName
            val crossProfileTarget = crossProfileTargets.firstOrNull()

            // Self-package installed in another profile is genuinely launchable via CrossProfileApps
            // without requiring ROLE_HOME or INTERACT_ACROSS_USERS.
            val verifiedSelfLaunchable = isSelfPackage && crossProfileTarget != null
            val verifiedExternalLaunchable = !isSelfPackage &&
                targetPkg != null &&
                resolvedExternalTarget != null &&
                hasLauncherPermission

            val verifiedTarget: VerifiedProfileTarget? = when {
                verifiedSelfLaunchable && crossProfileTarget != null -> {
                    val targetSerial = runCatching { backend.getUserSerialNumber(crossProfileTarget) }
                        .getOrDefault(-1L)
                    VerifiedProfileTarget(
                        userHandle = crossProfileTarget,
                        userSerial = targetSerial,
                        launchPath = ProfileLaunchPath.CROSS_PROFILE_APPS,
                        componentName = ComponentName(context.packageName, MainActivity::class.java.name),
                    )
                }
                verifiedExternalLaunchable && resolvedExternalTarget != null -> {
                    val (handle, component) = resolvedExternalTarget
                    val targetSerial = runCatching { backend.getUserSerialNumber(handle) }
                        .getOrDefault(-1L)
                    VerifiedProfileTarget(
                        userHandle = handle,
                        userSerial = targetSerial,
                        launchPath = ProfileLaunchPath.LAUNCHER_APPS,
                        componentName = component,
                    )
                }
                else -> null
            }

            val targetInstalledInSecondary = if (isSelfPackage) {
                selfInSecondary || targetVisibleInSecondary
            } else {
                targetVisibleInSecondary
            }

            val secondaryDetected = allSecondaryHandles.isNotEmpty()
            val canLaunchAcross = verifiedTarget != null
            val selectedSerial = verifiedTarget?.userSerial ?: secondarySerials.firstOrNull()

            val currentLabel = "Current Profile (Serial #$currentSerial)"
            val targetLabel = selectedSerial?.let { "Secondary Profile (Serial #$it)" }

            val summary = when {
                !secondaryDetected ->
                    "Only the primary personal profile is visible ($totalProfiles profile accessible)."
                verifiedSelfLaunchable ->
                    "Verified OS profile #$selectedSerial detected; same-package sandbox launchable via CrossProfileApps."
                verifiedExternalLaunchable ->
                    "Verified OS profile #$selectedSerial detected; target app launchable via LauncherApps."
                targetInstalledInSecondary ->
                    "Secondary profile #$selectedSerial detected with target app installed; cross-profile launch restricted by OS."
                anySecondaryQueryable ->
                    "Secondary profile #$selectedSerial detected ($totalProfiles profiles); target app not installed in that profile."
                else ->
                    "Secondary profile detected, but profile inspection is restricted by OS or device policy."
            }

            val limitation = when {
                !secondaryDetected ->
                    "No accessible Work Profile or secondary user profile detected. Non-DPC apps cannot provision profiles automatically."
                verifiedSelfLaunchable ->
                    "OS profile isolation is active for NovaCloner's companion instance via CrossProfileApps; third-party apps still require separate Work Profile installation and launcher permission."
                verifiedExternalLaunchable ->
                    "OS profile isolation is active via authorized LauncherApps access (max 1 Managed Work Profile per device)."
                !hasLauncherPermission ->
                    "Android blocks LauncherApps.startMainActivity for third-party apps in another profile unless NovaCloner holds default launcher role or privileged INTERACT_ACROSS_USERS."
                queryRestrictionNote != null ->
                    "Profile access constrained by system or OEM policy ($queryRestrictionNote)."
                else ->
                    "Install the target app inside your Work Profile to verify separate OS profile isolation."
            }

            val probe = WorkProfileProbeResult(
                totalAccessibleProfiles = totalProfiles,
                currentProfileSerial = currentSerial,
                secondaryProfileSerials = secondarySerials,
                selectedTargetSerial = selectedSerial,
                secondaryProfileDetected = secondaryDetected,
                secondaryProfileQueryAccessible = anySecondaryQueryable || selfInSecondary,
                targetAppVisibleInSecondaryProfile = targetInstalledInSecondary,
                selfInstalledInSecondaryProfile = selfInSecondary,
                crossProfileApiAvailable = crossProfileSupported,
                crossProfileTargetCount = crossProfileTargets.size,
                crossProfileCanInteract = crossProfileCanInteract,
                crossProfileCanRequestPermission = crossProfileCanRequest,
                hasLauncherShortcutPermission = hasLauncherPermission,
                canLaunchExternalAppAcrossProfiles = verifiedExternalLaunchable,
                capabilitySummary = summary,
                limitationNote = limitation,
            )

            val verification = buildVerificationReport(
                targetPkg = targetPkg,
                isSelfPackage = isSelfPackage,
                secondaryDetected = secondaryDetected,
                anySecondaryQueryable = anySecondaryQueryable || selfInSecondary,
                targetInstalledInSecondary = targetInstalledInSecondary,
                verifiedTarget = verifiedTarget,
                currentLabel = currentLabel,
                targetLabel = targetLabel,
                targetSerial = selectedSerial,
                queryRestrictionNote = queryRestrictionNote,
            )

            DetailedProfileEvaluation(
                probe = probe,
                verification = verification,
                verifiedTarget = verifiedTarget,
            )
        }

    private fun buildVerificationReport(
        targetPkg: String?,
        isSelfPackage: Boolean,
        secondaryDetected: Boolean,
        anySecondaryQueryable: Boolean,
        targetInstalledInSecondary: Boolean,
        verifiedTarget: VerifiedProfileTarget?,
        currentLabel: String,
        targetLabel: String?,
        targetSerial: Long?,
        queryRestrictionNote: String?,
    ): IsolationVerificationReport {
        if (!secondaryDetected) {
            return IsolationVerificationReport(
                status = IsolationVerificationStatus.NO_SECONDARY_PROFILE,
                launchPath = ProfileLaunchPath.UNAVAILABLE,
                currentProfileLabel = currentLabel,
                targetProfileLabel = null,
                targetProfileSerial = null,
                targetAppInstalledInProfile = false,
                launchPermittedInProfile = false,
                profileHasOwnAppData = false,
                isGenuineIsolationVerified = false,
                verificationSummary = "No secondary Android profile or Work Profile is present or accessible on this device.",
                guidedSetupSteps = listOf(
                    "Standard consumer apps cannot create an Android Work Profile automatically without Device Policy Controller (DPC) setup.",
                    "Provision a Work Profile externally via your device Settings, enterprise MDM, or a test DPC (such as Android TestDPC on a test device).",
                    "Install the target test app inside the newly created Work Profile.",
                    "Return to NovaCloner and refresh this workspace to verify the secondary profile.",
                ),
            )
        }

        if (!anySecondaryQueryable && !isSelfPackage) {
            return IsolationVerificationReport(
                status = IsolationVerificationStatus.PROFILE_ACCESS_RESTRICTED,
                launchPath = ProfileLaunchPath.UNAVAILABLE,
                currentProfileLabel = currentLabel,
                targetProfileLabel = targetLabel,
                targetProfileSerial = targetSerial,
                targetAppInstalledInProfile = false,
                launchPermittedInProfile = false,
                profileHasOwnAppData = false,
                isGenuineIsolationVerified = false,
                verificationSummary = "A secondary profile exists, but Android/OEM policy restricts querying its installed apps (${queryRestrictionNote ?: "access denied"}).",
                guidedSetupSteps = listOf(
                    "Check device Work Profile settings and verify the Work Profile is currently turned on (unlocked).",
                    "Note that OEM Private Space / App Twin features may hide profile packages from ordinary third-party apps.",
                    "For verifiable cross-profile testing, install NovaCloner into the authorized Work Profile as a companion probe.",
                ),
            )
        }

        if (targetPkg == null || !targetInstalledInSecondary) {
            return IsolationVerificationReport(
                status = IsolationVerificationStatus.APP_NOT_INSTALLED_IN_PROFILE,
                launchPath = ProfileLaunchPath.UNAVAILABLE,
                currentProfileLabel = currentLabel,
                targetProfileLabel = targetLabel,
                targetProfileSerial = targetSerial,
                targetAppInstalledInProfile = false,
                launchPermittedInProfile = false,
                profileHasOwnAppData = false,
                isGenuineIsolationVerified = false,
                verificationSummary = if (targetPkg == null) {
                    "Secondary profile is accessible, but no target package is linked to this workspace."
                } else {
                    "\"$targetPkg\" is not installed inside $targetLabel."
                },
                guidedSetupSteps = listOf(
                    "Open Google Play Store (Work tab) or your Work Profile installer outside NovaCloner.",
                    "Install \"${targetPkg ?: "your target test app"}\" inside the Work Profile so Android allocates an isolated /data/user/<id> directory.",
                    "Return to NovaCloner and tap Launch or refresh status to re-verify profile installation.",
                ),
            )
        }

        if (verifiedTarget == null) {
            return IsolationVerificationReport(
                status = IsolationVerificationStatus.CROSS_PROFILE_LAUNCH_RESTRICTED,
                launchPath = ProfileLaunchPath.UNAVAILABLE,
                currentProfileLabel = currentLabel,
                targetProfileLabel = targetLabel,
                targetProfileSerial = targetSerial,
                targetAppInstalledInProfile = true,
                launchPermittedInProfile = false,
                profileHasOwnAppData = true,
                isGenuineIsolationVerified = false,
                verificationSummary = "\"$targetPkg\" is installed in $targetLabel with its own OS data directory, but Android blocks launching third-party apps across profiles without default launcher or system permission.",
                guidedSetupSteps = listOf(
                    "Android allows LauncherApps.startMainActivity() across profiles only for the default Home launcher (ROLE_HOME) or privileged system apps.",
                    "Option A: Launch the Work Profile copy of \"$targetPkg\" directly from your device's Work tab in the system launcher.",
                    "Option B: To verify true OS profile isolation inside NovaCloner, link NovaCloner (${context.packageName}) installed inside the Work Profile (uses official CrossProfileApps API).",
                    "Option C: Switch this workspace back to \"Host App — Shared Data\" if you only want a direct personal-profile shortcut.",
                ),
            )
        }

        return IsolationVerificationReport(
            status = IsolationVerificationStatus.VERIFIED_WORK_PROFILE,
            launchPath = verifiedTarget.launchPath,
            currentProfileLabel = currentLabel,
            targetProfileLabel = "Secondary Profile (Serial #${verifiedTarget.userSerial})",
            targetProfileSerial = verifiedTarget.userSerial,
            targetAppInstalledInProfile = true,
            launchPermittedInProfile = true,
            profileHasOwnAppData = true,
            isGenuineIsolationVerified = true,
            verificationSummary = "Verified genuine OS isolation: \"$targetPkg\" is installed in Secondary Profile #${verifiedTarget.userSerial} with separate OS app data and permitted launch via ${verifiedTarget.launchPath.displayLabel}.",
            guidedSetupSteps = emptyList(),
        )
    }

    class AndroidProfileInspectionBackend(private val context: Context) : ProfileInspectionBackend {
        override fun myUserHandle(): UserHandle = Process.myUserHandle()

        override fun getUserProfiles(): List<UserHandle> {
            val userManager = context.getSystemService(UserManager::class.java) ?: return emptyList()
            return userManager.userProfiles.orEmpty()
        }

        override fun getUserSerialNumber(userHandle: UserHandle): Long {
            val userManager = context.getSystemService(UserManager::class.java) ?: return 0L
            return runCatching { userManager.getSerialNumberForUser(userHandle) }.getOrDefault(0L)
        }

        override fun isCrossProfileApiSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        override fun getCrossProfileTargets(): List<UserHandle> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
            val crossProfileApps = context.getSystemService(CrossProfileApps::class.java) ?: return emptyList()
            return crossProfileApps.targetUserProfiles.orEmpty()
        }

        override fun canInteractAcrossProfiles(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            val crossProfileApps = context.getSystemService(CrossProfileApps::class.java) ?: return false
            return runCatching { crossProfileApps.canInteractAcrossProfiles() }.getOrDefault(false)
        }

        override fun canRequestInteractAcrossProfiles(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            val crossProfileApps = context.getSystemService(CrossProfileApps::class.java) ?: return false
            return runCatching { crossProfileApps.canRequestInteractAcrossProfiles() }.getOrDefault(false)
        }

        override fun hasShortcutHostPermission(): Boolean {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            return runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
        }

        override fun canQueryActivitiesInProfile(
            userHandle: UserHandle,
            packageName: String?,
        ): ProfileQueryOutcome {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
                ?: return ProfileQueryOutcome.Restricted("LauncherApps service unavailable")
            return try {
                val queryPkg = packageName?.takeIf { it.isNotBlank() }
                val activities = launcherApps.getActivityList(queryPkg, userHandle)
                val packageEnabled = if (queryPkg != null) {
                    runCatching { launcherApps.isPackageEnabled(queryPkg, userHandle) }.getOrDefault(false)
                } else {
                    false
                }
                val firstComponent = activities.firstOrNull()?.componentName
                ProfileQueryOutcome.Accessible(
                    targetPackagePresent = queryPkg != null && (activities.isNotEmpty() || packageEnabled),
                    resolvedComponent = firstComponent,
                )
            } catch (se: SecurityException) {
                ProfileQueryOutcome.Restricted(se.localizedMessage ?: "SecurityException")
            } catch (e: Exception) {
                ProfileQueryOutcome.Restricted(e.localizedMessage ?: e::class.java.simpleName)
            }
        }
    }
}
