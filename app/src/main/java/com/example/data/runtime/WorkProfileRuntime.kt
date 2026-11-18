package com.example.data.runtime

import android.content.Context
import com.example.domain.model.ExecutionMode
import com.example.domain.model.Workspace
import com.example.domain.runtime.ContainerRuntime
import com.example.domain.runtime.RuntimeCapabilities
import com.example.domain.runtime.RuntimeLaunchResult
import com.example.domain.runtime.RuntimeMaintenanceResult
import com.example.domain.runtime.RuntimeProvisionResult

/**
 * Phase 1 read-only Work Profile capability runtime.
 * Detects accessible Android profiles safely without requesting privileged permissions,
 * provisioning a Managed Profile, or claiming cross-profile isolation where unavailable.
 */
class WorkProfileRuntime(
    private val workProfileDetector: WorkProfileDetector,
) : ContainerRuntime {

    override val mode: ExecutionMode = ExecutionMode.WORK_PROFILE

    override suspend fun probeCapabilities(packageName: String?): RuntimeCapabilities {
        val probe = workProfileDetector.inspect(packageName)

        // Phase 1 never provisions Managed Profiles and does not bypass OS launcher restrictions.
        val operationalForExternalLaunch = probe.canLaunchExternalAppAcrossProfiles &&
            !packageName.isNullOrBlank() &&
            probe.targetAppVisibleInSecondaryProfile

        val statusMessage = when {
            !probe.secondaryProfileDetected ->
                "Unavailable — no accessible secondary or Work Profile detected on device."
            !probe.hasLauncherShortcutPermission ->
                "Read-only detection — profile present, but cross-profile app launch requires launcher/system privilege."
            operationalForExternalLaunch ->
                "Secondary profile app visible (read-only phase; provisioning disabled)."
            else ->
                "Read-only detection — ${probe.capabilitySummary}"
        }

        val limitations = listOf(
            probe.limitationNote,
            "Android permits at most one Managed Work Profile per primary user.",
            "Nova Space does not provision or manage Work Profiles in Phase 1.",
        )

        return RuntimeCapabilities(
            mode = ExecutionMode.WORK_PROFILE,
            displayLabel = ExecutionMode.WORK_PROFILE.displayLabel,
            isOperational = false, // Strictly non-operational as a provisioning container in Phase 1
            providesIsolatedAppData = false,
            isVirtualized = false,
            statusMessage = statusMessage,
            limitations = limitations,
        )
    }

    override suspend fun provisionInstance(
        workspace: Workspace,
        packageName: String,
    ): RuntimeProvisionResult {
        return RuntimeProvisionResult.Unsupported(
            "Work Profile provisioning is not implemented in Phase 1 (read-only detection only)."
        )
    }

    override suspend fun launchInstance(
        context: Context,
        workspace: Workspace,
        packageName: String?,
    ): RuntimeLaunchResult {
        return RuntimeLaunchResult.Blocked(
            effectiveMode = ExecutionMode.WORK_PROFILE,
            reason = "Cross-profile runtime launching is disabled in Phase 1 (read-only profile detection).",
        )
    }

    override suspend fun clearInstanceData(workspace: Workspace): RuntimeMaintenanceResult {
        return RuntimeMaintenanceResult.Unsupported(
            "Cannot clear Work Profile app storage without Profile Owner privileges."
        )
    }

    override suspend fun destroyInstance(workspace: Workspace): RuntimeMaintenanceResult {
        return RuntimeMaintenanceResult.Unsupported(
            "Work Profile lifecycle management is disabled in Phase 1."
        )
    }
}
