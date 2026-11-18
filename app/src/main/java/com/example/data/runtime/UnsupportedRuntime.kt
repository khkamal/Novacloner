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

/**
 * Explicit non-operational runtime for unimplemented isolation backends (e.g. ISOLATED_CONTAINER)
 * or explicitly unsupported configurations. Never launches apps or claims isolation.
 */
class UnsupportedRuntime(
    override val mode: ExecutionMode = ExecutionMode.UNSUPPORTED,
    private val reason: String = "No operational application virtualization or container engine is installed.",
) : ContainerRuntime {

    override suspend fun probeCapabilities(packageName: String?): RuntimeCapabilities {
        val verificationReport = IsolationVerificationReport(
            status = IsolationVerificationStatus.UNSUPPORTED_CONTAINER_ENGINE,
            launchPath = ProfileLaunchPath.UNAVAILABLE,
            currentProfileLabel = "Current Host Profile",
            targetAppInstalledInProfile = false,
            launchPermittedInProfile = false,
            profileHasOwnAppData = false,
            isGenuineIsolationVerified = false,
            verificationSummary = reason,
            guidedSetupSteps = listOf(
                "An in-process APK virtualization engine is not supported for non-privileged apps under Google Play Dynamic Code Loading policies.",
                "Select \"Work Profile\" if you have provisioned a separate Android Work Profile outside NovaCloner, or \"Host App — Shared Data\" to launch the host app directly.",
            ),
        )
        return RuntimeCapabilities(
            mode = mode,
            displayLabel = mode.displayLabel,
            isOperational = false,
            providesIsolatedAppData = false,
            isVirtualized = false,
            statusMessage = reason,
            limitations = listOf(
                reason,
                "Normal non-root Android apps cannot execute independent cloned APK sandboxes without OS or external runtime support.",
            ),
            verificationReport = verificationReport,
        )
    }

    override suspend fun provisionInstance(
        workspace: Workspace,
        packageName: String,
    ): RuntimeProvisionResult = RuntimeProvisionResult.Unsupported(reason)

    override suspend fun launchInstance(
        context: Context,
        workspace: Workspace,
        packageName: String?,
    ): RuntimeLaunchResult = RuntimeLaunchResult.Blocked(
        effectiveMode = ExecutionMode.UNSUPPORTED,
        reason = reason,
    )

    override suspend fun clearInstanceData(workspace: Workspace): RuntimeMaintenanceResult =
        RuntimeMaintenanceResult.Unsupported(reason)

    override suspend fun destroyInstance(workspace: Workspace): RuntimeMaintenanceResult =
        RuntimeMaintenanceResult.Unsupported(reason)
}
