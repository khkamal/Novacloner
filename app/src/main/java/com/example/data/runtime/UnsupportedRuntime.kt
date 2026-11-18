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
 * Explicit non-operational runtime for unimplemented isolation backends (e.g. ISOLATED_CONTAINER)
 * or explicitly unsupported configurations. Never launches apps or claims isolation.
 */
class UnsupportedRuntime(
    override val mode: ExecutionMode = ExecutionMode.UNSUPPORTED,
    private val reason: String = "No operational application virtualization or container engine is installed.",
) : ContainerRuntime {

    override suspend fun probeCapabilities(packageName: String?): RuntimeCapabilities {
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
