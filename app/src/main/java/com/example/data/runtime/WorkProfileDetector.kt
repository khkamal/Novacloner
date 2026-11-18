package com.example.data.runtime

import android.content.Context
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.example.domain.model.WorkProfileProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Performs strictly read-only inspection of accessible Android user profiles using public SDK APIs.
 * Does not request privileged permissions, provision profiles, or bypass cross-profile boundaries.
 */
class WorkProfileDetector(
    private val context: Context,
    private val backend: ProfileInspectionBackend = AndroidProfileInspectionBackend(context),
) {

    interface ProfileInspectionBackend {
        fun myUserHandle(): UserHandle
        fun getUserProfiles(): List<UserHandle>
        fun getCrossProfileTargets(): List<UserHandle>
        fun isCrossProfileApiSupported(): Boolean
        fun hasShortcutHostPermission(): Boolean
        fun canQueryActivitiesInProfile(userHandle: UserHandle, packageName: String?): ProfileQueryOutcome
    }

    sealed interface ProfileQueryOutcome {
        data class Accessible(val targetPackagePresent: Boolean) : ProfileQueryOutcome
        data class Restricted(val reason: String) : ProfileQueryOutcome
    }

    suspend fun inspect(packageName: String? = null): WorkProfileProbeResult = withContext(Dispatchers.IO) {
        val currentHandle = runCatching { backend.myUserHandle() }.getOrNull()
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

        val secondaryDetected = secondaryProfiles.isNotEmpty() || crossProfileTargets.isNotEmpty()
        val hasLauncherPermission = runCatching { backend.hasShortcutHostPermission() }.getOrDefault(false)

        var anySecondaryQueryable = false
        var targetVisibleInSecondary = false
        var queryRestrictionNote: String? = null

        val candidateHandles = (secondaryProfiles + crossProfileTargets).distinct()
        for (profile in candidateHandles) {
            when (val outcome = backend.canQueryActivitiesInProfile(profile, packageName)) {
                is ProfileQueryOutcome.Accessible -> {
                    anySecondaryQueryable = true
                    if (outcome.targetPackagePresent) {
                        targetVisibleInSecondary = true
                    }
                }
                is ProfileQueryOutcome.Restricted -> {
                    if (queryRestrictionNote == null) {
                        queryRestrictionNote = outcome.reason
                    }
                }
            }
        }

        // Cross-profile launch of *other* apps via LauncherApps.startMainActivity requires either
        // the default launcher shortcut-host role or privileged INTERACT_ACROSS_USERS permission.
        val canLaunchAcross = secondaryDetected &&
            anySecondaryQueryable &&
            hasLauncherPermission &&
            (packageName.isNullOrBlank() || targetVisibleInSecondary)

        val summary = when {
            !secondaryDetected ->
                "Only the primary personal profile is visible ($totalProfiles profile accessible)."
            targetVisibleInSecondary && canLaunchAcross ->
                "Secondary profile detected ($totalProfiles profiles); target app visible with launcher access."
            targetVisibleInSecondary ->
                "Secondary profile detected ($totalProfiles profiles) with target app visible; cross-profile launch restricted by OS."
            anySecondaryQueryable ->
                "Secondary profile detected ($totalProfiles profiles); target app is not installed in that profile."
            else ->
                "Secondary profile detected, but profile inspection is restricted by OS or device policy."
        }

        val limitation = when {
            !secondaryDetected ->
                "No accessible Work Profile or secondary user profile detected. Provisioning is disabled in Phase 1."
            !hasLauncherPermission ->
                "Android restricts launching third-party apps in another profile unless Nova Space is a privileged/default launcher."
            queryRestrictionNote != null ->
                "Profile access constrained by system or OEM policy ($queryRestrictionNote)."
            else ->
                "Work Profile capability is read-only in Phase 1; no profile provisioning or data isolation is performed."
        }

        WorkProfileProbeResult(
            totalAccessibleProfiles = totalProfiles,
            secondaryProfileDetected = secondaryDetected,
            secondaryProfileQueryAccessible = anySecondaryQueryable,
            targetAppVisibleInSecondaryProfile = targetVisibleInSecondary,
            crossProfileApiAvailable = crossProfileSupported,
            crossProfileTargetCount = crossProfileTargets.size,
            hasLauncherShortcutPermission = hasLauncherPermission,
            canLaunchExternalAppAcrossProfiles = canLaunchAcross,
            capabilitySummary = summary,
            limitationNote = limitation,
        )
    }

    class AndroidProfileInspectionBackend(private val context: Context) : ProfileInspectionBackend {
        override fun myUserHandle(): UserHandle = Process.myUserHandle()

        override fun getUserProfiles(): List<UserHandle> {
            val userManager = context.getSystemService(UserManager::class.java) ?: return emptyList()
            return userManager.userProfiles.orEmpty()
        }

        override fun isCrossProfileApiSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        override fun getCrossProfileTargets(): List<UserHandle> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
            val crossProfileApps = context.getSystemService(CrossProfileApps::class.java) ?: return emptyList()
            return crossProfileApps.targetUserProfiles.orEmpty()
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
                val packagePresent = if (queryPkg != null) {
                    activities.isNotEmpty()
                } else {
                    false
                }
                ProfileQueryOutcome.Accessible(targetPackagePresent = packagePresent)
            } catch (se: SecurityException) {
                ProfileQueryOutcome.Restricted(se.localizedMessage ?: "SecurityException")
            } catch (e: Exception) {
                ProfileQueryOutcome.Restricted(e.localizedMessage ?: e::class.java.simpleName)
            }
        }
    }
}
