package com.example.data.runtime

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import com.example.domain.model.ExecutionMode
import com.example.domain.model.ProfileLaunchPath
import com.example.domain.model.Workspace
import com.example.domain.runtime.ContainerRuntime
import com.example.domain.runtime.RuntimeCapabilities
import com.example.domain.runtime.RuntimeLaunchResult
import com.example.domain.runtime.RuntimeMaintenanceResult
import com.example.domain.runtime.RuntimeProvisionResult

/**
 * Work Profile ContainerRuntime backed by official Android multi-profile verification.
 * Only reports operational isolation and executes launches when target profile presence,
 * target app installation, isolated data boundary, and launch permissions are verified.
 */
class WorkProfileRuntime(
    private val workProfileDetector: WorkProfileDetector,
    private val launchBackend: ProfileLaunchBackend? = null,
) : ContainerRuntime {

    interface ProfileLaunchBackend {
        fun launchOwnActivityInProfile(componentName: ComponentName, targetUser: UserHandle)
        fun launchExternalActivityInProfile(componentName: ComponentName, targetUser: UserHandle)
    }

    override val mode: ExecutionMode = ExecutionMode.WORK_PROFILE

    override suspend fun probeCapabilities(packageName: String?): RuntimeCapabilities {
        val eval = workProfileDetector.verifyWorkProfileIsolation(packageName)
        val report = eval.verification
        val verified = report.isGenuineIsolationVerified && eval.verifiedTarget != null

        val limitations = buildList {
            if (!verified) {
                add(report.verificationSummary)
            }
            add(eval.probe.limitationNote)
            add("Android permits at most one Managed Work Profile per primary user.")
            add("Work Profile provisioning cannot be triggered automatically without user/DPC enrollment outside NovaCloner.")
        }.distinct()

        return RuntimeCapabilities(
            mode = ExecutionMode.WORK_PROFILE,
            displayLabel = if (verified) {
                "Work Profile — OS Profile Isolated"
            } else {
                ExecutionMode.WORK_PROFILE.displayLabel
            },
            isOperational = verified,
            providesIsolatedAppData = verified && report.profileHasOwnAppData,
            isVirtualized = verified,
            statusMessage = report.verificationSummary,
            limitations = limitations,
            verificationReport = report,
        )
    }

    override suspend fun provisionInstance(
        workspace: Workspace,
        packageName: String,
    ): RuntimeProvisionResult {
        val eval = workProfileDetector.verifyWorkProfileIsolation(packageName)
        return if (eval.verification.isGenuineIsolationVerified && eval.verifiedTarget != null) {
            RuntimeProvisionResult.Ready(
                effectiveMode = ExecutionMode.WORK_PROFILE,
                isolatedAppData = true,
                message = eval.verification.verificationSummary,
            )
        } else {
            RuntimeProvisionResult.Unsupported(eval.verification.verificationSummary)
        }
    }

    override suspend fun launchInstance(
        context: Context,
        workspace: Workspace,
        packageName: String?,
    ): RuntimeLaunchResult {
        val eval = workProfileDetector.verifyWorkProfileIsolation(packageName)
        val target = eval.verifiedTarget
        if (!eval.verification.isGenuineIsolationVerified || target == null) {
            return RuntimeLaunchResult.Blocked(
                effectiveMode = ExecutionMode.WORK_PROFILE,
                reason = eval.verification.verificationSummary,
            )
        }

        val backend = launchBackend ?: AndroidProfileLaunchBackend(context)
        return try {
            when (target.launchPath) {
                ProfileLaunchPath.CROSS_PROFILE_APPS -> {
                    backend.launchOwnActivityInProfile(target.componentName, target.userHandle)
                    RuntimeLaunchResult.Success(
                        effectiveMode = ExecutionMode.WORK_PROFILE,
                        isolatedAppData = true,
                    )
                }
                ProfileLaunchPath.LAUNCHER_APPS -> {
                    backend.launchExternalActivityInProfile(target.componentName, target.userHandle)
                    RuntimeLaunchResult.Success(
                        effectiveMode = ExecutionMode.WORK_PROFILE,
                        isolatedAppData = true,
                    )
                }
                else -> RuntimeLaunchResult.Blocked(
                    effectiveMode = ExecutionMode.WORK_PROFILE,
                    reason = eval.verification.verificationSummary,
                )
            }
        } catch (se: SecurityException) {
            RuntimeLaunchResult.Blocked(
                effectiveMode = ExecutionMode.WORK_PROFILE,
                reason = "Android blocked cross-profile launch: ${se.localizedMessage ?: "SecurityException"}.",
            )
        } catch (anf: ActivityNotFoundException) {
            RuntimeLaunchResult.Failure(
                reason = "Target activity was not found inside secondary profile #${target.userSerial}: ${anf.localizedMessage ?: "not found"}.",
            )
        } catch (e: Exception) {
            RuntimeLaunchResult.Failure(
                reason = e.localizedMessage ?: "Failed to launch target app in secondary profile.",
            )
        }
    }

    override suspend fun clearInstanceData(workspace: Workspace): RuntimeMaintenanceResult {
        return RuntimeMaintenanceResult.Unsupported(
            "Android prohibits third-party apps from wiping another OS profile's application storage directly."
        )
    }

    override suspend fun destroyInstance(workspace: Workspace): RuntimeMaintenanceResult {
        return RuntimeMaintenanceResult.Unsupported(
            "Deleting a NovaCloner workspace removes only workspace metadata; manage or remove Work Profile apps via Android Settings."
        )
    }

    class AndroidProfileLaunchBackend(private val context: Context) : ProfileLaunchBackend {
        override fun launchOwnActivityInProfile(
            componentName: ComponentName,
            targetUser: UserHandle,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                throw SecurityException("CrossProfileApps requires Android 9 (API 28) or higher.")
            }
            val crossProfileApps = context.getSystemService(CrossProfileApps::class.java)
                ?: throw IllegalStateException("CrossProfileApps system service unavailable.")
            crossProfileApps.startMainActivity(componentName, targetUser)
        }

        override fun launchExternalActivityInProfile(
            componentName: ComponentName,
            targetUser: UserHandle,
        ) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
                ?: throw IllegalStateException("LauncherApps system service unavailable.")
            launcherApps.startMainActivity(componentName, targetUser, null, null)
        }
    }
}
