package com.example.data.runtime

import android.content.Context
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.AppCompatibilityReport
import com.example.domain.model.ExecutionMode
import com.example.domain.model.WorkProfileProbeResult
import com.example.domain.model.Workspace
import com.example.domain.runtime.ContainerRuntime
import com.example.domain.runtime.ResolvedRuntimeState
import com.example.domain.runtime.RuntimeCapabilities
import com.example.domain.runtime.RuntimeLaunchResult

/**
 * Selects the operational ContainerRuntime for a Workspace without ever dispatching
 * launches to unimplemented runtimes or falsely reporting isolated virtualization.
 */
class ContainerRuntimeRegistry(
    private val hostDirectRuntime: ContainerRuntime,
    private val workProfileRuntime: ContainerRuntime,
    private val isolatedContainerRuntime: ContainerRuntime = UnsupportedRuntime(
        mode = ExecutionMode.ISOLATED_CONTAINER,
        reason = "Isolated virtual container engine is not implemented in Phase 1.",
    ),
    private val unsupportedRuntime: ContainerRuntime = UnsupportedRuntime(
        mode = ExecutionMode.UNSUPPORTED,
    ),
    private val workProfileDetector: WorkProfileDetector,
    private val compatibilityAnalyzer: AppCompatibilityAnalyzer,
    private val workspaceRepository: WorkspaceRepository? = null,
) {

    fun runtimeForMode(mode: ExecutionMode): ContainerRuntime {
        return when (mode) {
            ExecutionMode.HOST_DIRECT -> hostDirectRuntime
            ExecutionMode.WORK_PROFILE -> workProfileRuntime
            ExecutionMode.ISOLATED_CONTAINER -> isolatedContainerRuntime
            ExecutionMode.UNSUPPORTED -> unsupportedRuntime
        }
    }

    suspend fun resolveRuntimeState(workspace: Workspace): ResolvedRuntimeState {
        val pkg = workspace.packageName?.takeIf { it.isNotBlank() }
        val requestedMode = workspace.executionMode
        val workProfileProbe: WorkProfileProbeResult = workProfileDetector.inspect(pkg)
        val compatibility: AppCompatibilityReport? = compatibilityAnalyzer.analyze(pkg)

        val requestedRuntime = runtimeForMode(requestedMode)
        val requestedCaps = requestedRuntime.probeCapabilities(pkg)
        val hostCaps = hostDirectRuntime.probeCapabilities(pkg)

        // Never treat an unoperational runtime (e.g., ISOLATED_CONTAINER or read-only WORK_PROFILE)
        // as active virtualization. Fall back cleanly to HostDirectRuntime when appropriate, or UNSUPPORTED.
        val selectedCaps: RuntimeCapabilities
        val fallbackApplied: Boolean

        when {
            requestedMode == ExecutionMode.UNSUPPORTED -> {
                selectedCaps = unsupportedRuntime.probeCapabilities(pkg)
                fallbackApplied = false
            }
            requestedCaps.isOperational -> {
                selectedCaps = requestedCaps
                fallbackApplied = false
            }
            pkg != null && hostCaps.isOperational -> {
                selectedCaps = hostCaps
                fallbackApplied = requestedMode != ExecutionMode.HOST_DIRECT
            }
            pkg == null -> {
                selectedCaps = hostCaps
                fallbackApplied = requestedMode != ExecutionMode.HOST_DIRECT
            }
            else -> {
                selectedCaps = RuntimeCapabilities(
                    mode = ExecutionMode.UNSUPPORTED,
                    displayLabel = ExecutionMode.UNSUPPORTED.displayLabel,
                    isOperational = false,
                    providesIsolatedAppData = false,
                    isVirtualized = false,
                    statusMessage = hostCaps.statusMessage,
                    limitations = hostCaps.limitations,
                )
                fallbackApplied = requestedMode != ExecutionMode.UNSUPPORTED
            }
        }

        val effectiveMode = selectedCaps.mode
        val isVirtualized = selectedCaps.isOperational &&
            selectedCaps.providesIsolatedAppData &&
            selectedCaps.isVirtualized

        val runtimeStatusLabel = when {
            pkg == null -> "Awaiting App Link (${effectiveMode.shortBadge})"
            !selectedCaps.isOperational -> "Unavailable"
            fallbackApplied -> "Fallback Active (${effectiveMode.shortBadge})"
            isVirtualized -> "Isolated Runtime Active"
            else -> "Operational (Direct Host Launch)"
        }

        val canLaunch = pkg != null &&
            selectedCaps.isOperational &&
            effectiveMode != ExecutionMode.UNSUPPORTED

        val combinedLimitations = buildList {
            if (fallbackApplied) {
                add("${requestedMode.displayLabel} is not operational; safely fell back to ${effectiveMode.displayLabel}.")
            }
            addAll(selectedCaps.limitations)
            if (workProfileProbe.secondaryProfileDetected && effectiveMode != ExecutionMode.WORK_PROFILE) {
                add("Work Profile check: ${workProfileProbe.capabilitySummary}")
            }
        }.distinct()

        return ResolvedRuntimeState(
            requestedMode = requestedMode,
            effectiveMode = effectiveMode,
            executionModeLabel = effectiveMode.displayLabel,
            runtimeStatusLabel = runtimeStatusLabel,
            isRuntimeOperational = selectedCaps.isOperational,
            providesIsolatedAppData = isVirtualized && selectedCaps.providesIsolatedAppData,
            isVirtualized = isVirtualized,
            canLaunch = canLaunch,
            fallbackApplied = fallbackApplied,
            statusDetail = selectedCaps.statusMessage,
            limitations = combinedLimitations,
            workProfileProbe = workProfileProbe,
            compatibilityReport = compatibility,
        )
    }

    /**
     * Unified launch entrypoint shared by HomeViewModel and WorkspaceDetailViewModel.
     * Guarantees unimplemented runtimes are never dispatched and launch metrics stay synchronized.
     */
    suspend fun launchWorkspaceApp(
        context: Context,
        workspace: Workspace,
    ): RuntimeLaunchResult {
        val pkg = workspace.packageName?.takeIf { it.isNotBlank() }
            ?: return RuntimeLaunchResult.Blocked(
                effectiveMode = ExecutionMode.HOST_DIRECT,
                reason = "No installed application is linked to this workspace yet.",
            )

        val resolved = resolveRuntimeState(workspace)
        if (!resolved.isRuntimeOperational || resolved.effectiveMode == ExecutionMode.UNSUPPORTED) {
            return RuntimeLaunchResult.Blocked(
                effectiveMode = ExecutionMode.UNSUPPORTED,
                reason = resolved.statusDetail,
            )
        }

        val targetRuntime = runtimeForMode(resolved.effectiveMode)
        val caps = targetRuntime.probeCapabilities(pkg)
        if (!caps.isOperational) {
            return RuntimeLaunchResult.Blocked(
                effectiveMode = ExecutionMode.UNSUPPORTED,
                reason = caps.statusMessage,
            )
        }

        val result = targetRuntime.launchInstance(context, workspace, pkg)
        if (result is RuntimeLaunchResult.Success) {
            workspaceRepository?.recordAppLaunch(workspace.id)
        }
        return result
    }
}
