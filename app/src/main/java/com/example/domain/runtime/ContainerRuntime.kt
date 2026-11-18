package com.example.domain.runtime

import android.content.Context
import com.example.domain.model.AppCompatibilityReport
import com.example.domain.model.ExecutionMode
import com.example.domain.model.WorkProfileProbeResult
import com.example.domain.model.Workspace
import com.example.utils.LaunchResult

data class RuntimeCapabilities(
    val mode: ExecutionMode,
    val displayLabel: String,
    val isOperational: Boolean,
    val providesIsolatedAppData: Boolean,
    val isVirtualized: Boolean,
    val statusMessage: String,
    val limitations: List<String>,
)

sealed interface RuntimeProvisionResult {
    data class Ready(
        val effectiveMode: ExecutionMode,
        val isolatedAppData: Boolean,
        val message: String,
    ) : RuntimeProvisionResult

    data class Unsupported(val reason: String) : RuntimeProvisionResult
    data class Failed(val reason: String) : RuntimeProvisionResult
}

sealed interface RuntimeLaunchResult {
    data class Success(
        val effectiveMode: ExecutionMode,
        val isolatedAppData: Boolean,
    ) : RuntimeLaunchResult

    data class Blocked(
        val effectiveMode: ExecutionMode,
        val reason: String,
    ) : RuntimeLaunchResult

    data class Failure(
        val reason: String,
        val hostLaunchResult: LaunchResult? = null,
    ) : RuntimeLaunchResult
}

sealed interface RuntimeMaintenanceResult {
    data class Completed(val message: String) : RuntimeMaintenanceResult
    data class NotApplicable(val reason: String) : RuntimeMaintenanceResult
    data class Unsupported(val reason: String) : RuntimeMaintenanceResult
    data class Failed(val reason: String) : RuntimeMaintenanceResult
}

data class ResolvedRuntimeState(
    val requestedMode: ExecutionMode,
    val effectiveMode: ExecutionMode,
    val executionModeLabel: String,
    val runtimeStatusLabel: String,
    val isRuntimeOperational: Boolean,
    val providesIsolatedAppData: Boolean,
    val isVirtualized: Boolean,
    val canLaunch: Boolean,
    val fallbackApplied: Boolean,
    val statusDetail: String,
    val limitations: List<String>,
    val workProfileProbe: WorkProfileProbeResult,
    val compatibilityReport: AppCompatibilityReport? = null,
)

interface ContainerRuntime {
    val mode: ExecutionMode

    suspend fun probeCapabilities(packageName: String? = null): RuntimeCapabilities

    suspend fun provisionInstance(
        workspace: Workspace,
        packageName: String,
    ): RuntimeProvisionResult

    suspend fun launchInstance(
        context: Context,
        workspace: Workspace,
        packageName: String?,
    ): RuntimeLaunchResult

    suspend fun clearInstanceData(workspace: Workspace): RuntimeMaintenanceResult

    suspend fun destroyInstance(workspace: Workspace): RuntimeMaintenanceResult
}
