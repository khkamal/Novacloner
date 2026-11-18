package com.example.data.runtime

import android.content.Context
import com.example.domain.model.ExecutionMode
import com.example.domain.model.IsolationVerificationReport
import com.example.domain.model.IsolationVerificationStatus
import com.example.domain.model.ProfileLaunchPath
import com.example.domain.model.Workspace
import com.example.domain.runtime.ContainerRuntime
import com.example.domain.runtime.RuntimeCapabilities
import com.example.domain.runtime.RuntimeLaunchResult
import com.example.domain.runtime.RuntimeMaintenanceResult
import com.example.domain.runtime.RuntimeProvisionResult
import com.example.utils.AppLaunchHelper
import com.example.utils.LaunchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launches the host device's installed application directly via Android PackageManager.
 * Does not clone apps or isolate app data; accurately reports shared host execution.
 */
class HostDirectRuntime(
    private val context: Context,
) : ContainerRuntime {

    override val mode: ExecutionMode = ExecutionMode.HOST_DIRECT

    override suspend fun probeCapabilities(packageName: String?): RuntimeCapabilities =
        withContext(Dispatchers.IO) {
            val target = packageName?.trim()?.takeIf { it.isNotEmpty() }
            val installed = target != null && AppLaunchHelper.isPackageInstalled(context, target)
            val launchable = target != null && installed &&
                context.packageManager.getLaunchIntentForPackage(target) != null

            val statusMessage = when {
                target == null -> "Ready for direct host association (no app linked yet)."
                !installed -> "\"$target\" is not currently installed on this device."
                !launchable -> "\"$target\" has no launchable MAIN/LAUNCHER activity."
                else -> "Operational — launches host installation directly via PackageManager."
            }

            val verificationReport = IsolationVerificationReport(
                status = IsolationVerificationStatus.HOST_SHARED_DATA,
                launchPath = if (launchable) ProfileLaunchPath.HOST_PACKAGE_MANAGER else ProfileLaunchPath.UNAVAILABLE,
                currentProfileLabel = "Current Host Profile",
                targetProfileLabel = "Current Host Profile (Same User)",
                targetAppInstalledInProfile = installed,
                launchPermittedInProfile = launchable,
                profileHasOwnAppData = false,
                isGenuineIsolationVerified = false,
                verificationSummary = "Launches the single host installation in your current Android profile with shared app data.",
                guidedSetupSteps = emptyList(),
            )

            RuntimeCapabilities(
                mode = ExecutionMode.HOST_DIRECT,
                displayLabel = ExecutionMode.HOST_DIRECT.displayLabel,
                isOperational = target == null || (installed && launchable),
                providesIsolatedAppData = false,
                isVirtualized = false,
                statusMessage = statusMessage,
                limitations = HOST_LIMITATIONS,
                verificationReport = verificationReport,
            )
        }

    override suspend fun provisionInstance(
        workspace: Workspace,
        packageName: String,
    ): RuntimeProvisionResult = withContext(Dispatchers.IO) {
        if (!AppLaunchHelper.isPackageInstalled(context, packageName)) {
            return@withContext RuntimeProvisionResult.Failed(
                "\"$packageName\" is not installed on this device."
            )
        }
        val hasIntent = context.packageManager.getLaunchIntentForPackage(packageName) != null
        if (!hasIntent) {
            return@withContext RuntimeProvisionResult.Failed(
                "\"$packageName\" does not expose a launchable activity."
            )
        }
        RuntimeProvisionResult.Ready(
            effectiveMode = ExecutionMode.HOST_DIRECT,
            isolatedAppData = false,
            message = "Linked to host installation with shared device storage (not cloned).",
        )
    }

    override suspend fun launchInstance(
        context: Context,
        workspace: Workspace,
        packageName: String?,
    ): RuntimeLaunchResult {
        return when (val result = AppLaunchHelper.launchApp(context, packageName)) {
            LaunchResult.Success -> RuntimeLaunchResult.Success(
                effectiveMode = ExecutionMode.HOST_DIRECT,
                isolatedAppData = false,
            )
            else -> RuntimeLaunchResult.Failure(
                reason = AppLaunchHelper.messageForFailure(result),
                hostLaunchResult = result,
            )
        }
    }

    override suspend fun clearInstanceData(workspace: Workspace): RuntimeMaintenanceResult {
        return RuntimeMaintenanceResult.NotApplicable(
            "Host Direct mode uses the device's shared app storage; Android prevents third-party apps from wiping another package's data."
        )
    }

    override suspend fun destroyInstance(workspace: Workspace): RuntimeMaintenanceResult {
        return RuntimeMaintenanceResult.NotApplicable(
            "No isolated container exists for Host Direct workspaces; only Nova Space metadata is removed when deleting a workspace."
        )
    }

    companion object {
        val HOST_LIMITATIONS = listOf(
            "Launches the single original installation on this device (not a separate clone).",
            "App accounts, login sessions, and storage are shared with the host system.",
            "Workspace Device Identity values are local reference metadata and are not injected into external apps.",
        )
    }
}
