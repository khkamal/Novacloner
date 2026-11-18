package com.example.data.runtime

import android.content.Context
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.AppCompatibilityReport
import com.example.domain.model.ExecutionMode
import com.example.domain.model.IsolationVerificationReport
import com.example.domain.model.IsolationVerificationStatus
import com.example.domain.model.ProfileLaunchPath
import com.example.domain.model.WorkProfileProbeResult
import com.example.domain.model.Workspace
import com.example.domain.runtime.ContainerRuntime
import com.example.domain.runtime.ResolvedRuntimeState
import com.example.domain.runtime.RuntimeCapabilities
import com.example.domain.runtime.RuntimeLaunchResult

/**
 * Resolves the exact ContainerRuntime for a Workspace without silently falling back to the
 * original host app when an isolated mode (WORK_PROFILE / ISOLATED_CONTAINER) is unavailable.
 */
class ContainerRuntimeRegistry(
    private val hostDirectRuntime: ContainerRuntime,
    private val workProfileRuntime: ContainerRuntime,
    private val isolatedContainerRuntime: ContainerRuntime = UnsupportedRuntime(
        mode = ExecutionMode.ISOLATED_CONTAINER,
        reason = "In-process APK container virtualization is unavailable on non-privileged Android without violating Play Dynamic Code Loading rules.",
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
        val profileEval = workProfileDetector.verifyWorkProfileIsolation(pkg)
        val workProfileProbe: WorkProfileProbeResult = profileEval.probe
        val compatibility: AppCompatibilityReport? = compatibilityAnalyzer.analyze(pkg)

        val requestedRuntime = runtimeForMode(requestedMode)
        val requestedCaps = requestedRuntime.probeCapabilities(pkg)

        val effectiveMode: ExecutionMode
        val selectedCaps: RuntimeCapabilities
        val verification: IsolationVerificationReport

        when (requestedMode) {
            ExecutionMode.HOST_DIRECT -> {
                effectiveMode = ExecutionMode.HOST_DIRECT
                selectedCaps = requestedCaps
                verification = requestedCaps.verificationReport ?: IsolationVerificationReport(
                    status = IsolationVerificationStatus.HOST_SHARED_DATA,
                    launchPath = if (requestedCaps.isOperational && pkg != null) {
                        ProfileLaunchPath.HOST_PACKAGE_MANAGER
                    } else {
                        ProfileLaunchPath.UNAVAILABLE
                    },
                    currentProfileLabel = "Current Profile (Serial #${workProfileProbe.currentProfileSerial})",
                    targetAppInstalledInProfile = requestedCaps.isOperational && pkg != null,
                    launchPermittedInProfile = requestedCaps.isOperational && pkg != null,
                    profileHasOwnAppData = false,
                    isGenuineIsolationVerified = false,
                    verificationSummary = requestedCaps.statusMessage,
                )
            }
            ExecutionMode.WORK_PROFILE -> {
                // Keep effectiveMode = WORK_PROFILE rather than silently falling back to HOST_DIRECT.
                effectiveMode = ExecutionMode.WORK_PROFILE
                selectedCaps = requestedCaps
                verification = requestedCaps.verificationReport ?: profileEval.verification
            }
            ExecutionMode.ISOLATED_CONTAINER,
            ExecutionMode.UNSUPPORTED -> {
                effectiveMode = if (requestedCaps.isOperational && requestedCaps.isVirtualized) {
                    requestedMode
                } else {
                    ExecutionMode.UNSUPPORTED
                }
                selectedCaps = requestedCaps
                verification = requestedCaps.verificationReport ?: IsolationVerificationReport(
                    status = IsolationVerificationStatus.UNSUPPORTED_CONTAINER_ENGINE,
                    launchPath = ProfileLaunchPath.UNAVAILABLE,
                    currentProfileLabel = "Current Profile (Serial #${workProfileProbe.currentProfileSerial})",
                    verificationSummary = requestedCaps.statusMessage,
                )
            }
        }

        val isVirtualized = selectedCaps.isOperational &&
            selectedCaps.providesIsolatedAppData &&
            selectedCaps.isVirtualized &&
            verification.isGenuineIsolationVerified

        val runtimeAvailabilityLabel = when {
            pkg == null && effectiveMode == ExecutionMode.HOST_DIRECT -> "Ready (Awaiting App Link)"
            selectedCaps.isOperational -> "Available"
            effectiveMode == ExecutionMode.UNSUPPORTED -> "Unsupported"
            else -> "Blocked / Setup Required"
        }

        val profileAvailabilityLabel = when {
            effectiveMode == ExecutionMode.HOST_DIRECT ->
                "Primary Profile #${workProfileProbe.currentProfileSerial} (Host Only)"
            workProfileProbe.secondaryProfileDetected && workProfileProbe.selectedTargetSerial != null ->
                "Secondary Profile #${workProfileProbe.selectedTargetSerial} Detected"
            workProfileProbe.secondaryProfileDetected ->
                "Secondary Profile Detected"
            else ->
                "No Secondary Profile Available"
        }

        val targetAppStatusInProfileLabel = when {
            pkg == null -> "No App Linked"
            effectiveMode == ExecutionMode.HOST_DIRECT ->
                if (compatibility?.isInstalled == true) "Installed in Host Profile" else "Not Installed on Host"
            verification.targetAppInstalledInProfile ->
                "Installed in ${verification.targetProfileLabel ?: "Target Profile"}"
            else ->
                "Not Installed in Secondary Profile"
        }

        val launchCapabilityLabel = when {
            pkg == null -> "Select an App First"
            effectiveMode == ExecutionMode.UNSUPPORTED -> "Blocked — No Container Engine"
            effectiveMode == ExecutionMode.WORK_PROFILE && !verification.launchPermittedInProfile ->
                "Blocked — ${verification.launchPath.displayLabel}"
            effectiveMode == ExecutionMode.WORK_PROFILE && verification.launchPermittedInProfile ->
                "Permitted — ${verification.launchPath.displayLabel}"
            selectedCaps.isOperational ->
                "Direct Launch — Host PackageManager"
            else ->
                "Unavailable on Host"
        }

        val isolationStatusLabel = if (isVirtualized) {
            "Verified — Separate OS Profile Sandbox"
        } else {
            verification.status.displayLabel
        }

        val runtimeStatusLabel = when {
            pkg == null -> "Awaiting App Link (${effectiveMode.shortBadge})"
            isVirtualized -> "Operational (Verified OS Isolation)"
            effectiveMode == ExecutionMode.HOST_DIRECT && selectedCaps.isOperational ->
                "Operational (Direct Host Launch)"
            effectiveMode == ExecutionMode.WORK_PROFILE ->
                "Blocked (${verification.status.displayLabel})"
            else -> "Unsupported"
        }

        val canLaunch = pkg != null &&
            selectedCaps.isOperational &&
            effectiveMode != ExecutionMode.UNSUPPORTED

        val combinedLimitations = buildList {
            addAll(selectedCaps.limitations)
            if (effectiveMode == ExecutionMode.HOST_DIRECT && workProfileProbe.secondaryProfileDetected) {
                add("Work Profile check: ${workProfileProbe.capabilitySummary}")
            }
        }.distinct()

        return ResolvedRuntimeState(
            requestedMode = requestedMode,
            effectiveMode = effectiveMode,
            executionModeLabel = when {
                effectiveMode == ExecutionMode.WORK_PROFILE && isVirtualized ->
                    "Work Profile — OS Isolated Instance"
                effectiveMode == ExecutionMode.WORK_PROFILE ->
                    "Work Profile (Blocked — No Verified Launch)"
                else -> effectiveMode.displayLabel
            },
            runtimeStatusLabel = runtimeStatusLabel,
            runtimeAvailabilityLabel = runtimeAvailabilityLabel,
            profileAvailabilityLabel = profileAvailabilityLabel,
            targetAppStatusInProfileLabel = targetAppStatusInProfileLabel,
            launchCapabilityLabel = launchCapabilityLabel,
            isolationStatusLabel = isolationStatusLabel,
            isRuntimeOperational = selectedCaps.isOperational,
            providesIsolatedAppData = isVirtualized,
            isVirtualized = isVirtualized,
            canLaunch = canLaunch,
            fallbackApplied = false,
            statusDetail = selectedCaps.statusMessage,
            limitations = combinedLimitations,
            workProfileProbe = workProfileProbe,
            isolationVerification = verification,
            compatibilityReport = compatibility,
        )
    }

    /**
     * Unified launch entrypoint shared by HomeViewModel and WorkspaceDetailViewModel.
     * Guarantees unimplemented or unverified runtimes are never dispatched and never
     * silently launch the host app under a clone label.
     */
    suspend fun launchWorkspaceApp(
        context: Context,
        workspace: Workspace,
    ): RuntimeLaunchResult {
        val pkg = workspace.packageName?.takeIf { it.isNotBlank() }
            ?: return RuntimeLaunchResult.Blocked(
                effectiveMode = workspace.executionMode,
                reason = "No installed application is linked to this workspace yet.",
            )

        val resolved = resolveRuntimeState(workspace)
        if (!resolved.isRuntimeOperational || resolved.effectiveMode == ExecutionMode.UNSUPPORTED) {
            return RuntimeLaunchResult.Blocked(
                effectiveMode = resolved.effectiveMode,
                reason = resolved.statusDetail,
            )
        }

        val targetRuntime = runtimeForMode(resolved.effectiveMode)
        val caps = targetRuntime.probeCapabilities(pkg)
        if (!caps.isOperational) {
            return RuntimeLaunchResult.Blocked(
                effectiveMode = resolved.effectiveMode,
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
