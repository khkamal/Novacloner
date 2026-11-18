package com.example.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceInfoRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.AppAvailability
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.DeviceInfoSnapshot
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.Workspace
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkspaceDeviceInfoUiState(
    val isLoading: Boolean = true,
    val workspace: Workspace? = null,
    val availability: AppAvailability = AppAvailability.NO_APP_LINKED,
    val installedAppDetails: InstalledAppInfo? = null,
    val defaultCardBehavior: DefaultWorkspaceBehavior = DefaultWorkspaceBehavior.OPEN_DETAILS,
    val hostDeviceSnapshot: DeviceInfoSnapshot? = null,
)

sealed interface WorkspaceDeviceInfoEvent {
    data class ShowMessage(val message: String) : WorkspaceDeviceInfoEvent
}

class WorkspaceDeviceInfoViewModel(
    private val workspaceId: Long,
    private val workspaceRepository: WorkspaceRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val availabilityFlow = MutableStateFlow(AppAvailability.NO_APP_LINKED)
    private val installedDetailsFlow = MutableStateFlow<InstalledAppInfo?>(null)
    private val hostSnapshotFlow = MutableStateFlow<DeviceInfoSnapshot?>(null)

    private val _events = MutableSharedFlow<WorkspaceDeviceInfoEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<WorkspaceDeviceInfoUiState> = combine(
        workspaceRepository.observeWorkspace(workspaceId),
        availabilityFlow,
        installedDetailsFlow,
        settingsRepository.userPreferences,
        hostSnapshotFlow,
    ) { workspace, availability, appDetails, prefs, hostSnapshot ->
        WorkspaceDeviceInfoUiState(
            isLoading = false,
            workspace = workspace,
            availability = availability,
            installedAppDetails = appDetails,
            defaultCardBehavior = prefs.defaultWorkspaceBehavior,
            hostDeviceSnapshot = hostSnapshot,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkspaceDeviceInfoUiState(),
    )

    init {
        viewModelScope.launch {
            hostSnapshotFlow.value = runCatching {
                deviceInfoRepository.getDeviceInfoSnapshot()
            }.getOrNull()
        }
        viewModelScope.launch {
            workspaceRepository.observeWorkspace(workspaceId).collect { ws ->
                refreshLinkedAppMetadata(ws)
            }
        }
    }

    fun refresh(workspace: Workspace? = uiState.value.workspace) {
        viewModelScope.launch {
            hostSnapshotFlow.value = runCatching {
                deviceInfoRepository.getDeviceInfoSnapshot()
            }.getOrNull()
            refreshLinkedAppMetadata(workspace)
        }
    }

    private suspend fun refreshLinkedAppMetadata(workspace: Workspace?) {
        val pkg = workspace?.packageName
        if (pkg.isNullOrBlank()) {
            availabilityFlow.value = AppAvailability.NO_APP_LINKED
            installedDetailsFlow.value = null
            return
        }
        val isInstalled = installedAppsRepository.isAppInstalled(pkg)
        availabilityFlow.value = if (isInstalled) {
            AppAvailability.AVAILABLE
        } else {
            AppAvailability.UNAVAILABLE
        }
        installedDetailsFlow.value = if (isInstalled) {
            installedAppsRepository.getInstalledAppDetails(pkg)
        } else {
            null
        }
    }

    fun regenerateWorkspaceId() {
        viewModelScope.launch {
            val newId = workspaceRepository.regenerateWorkspaceGeneratedId(workspaceId)
            if (newId != null) {
                _events.emit(
                    WorkspaceDeviceInfoEvent.ShowMessage("Workspace ID regenerated for this Workspace.")
                )
            }
        }
    }

    class Factory(
        private val workspaceId: Long,
        private val workspaceRepository: WorkspaceRepository,
        private val installedAppsRepository: InstalledAppsRepository,
        private val deviceInfoRepository: DeviceInfoRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkspaceDeviceInfoViewModel(
                workspaceId = workspaceId,
                workspaceRepository = workspaceRepository,
                installedAppsRepository = installedAppsRepository,
                deviceInfoRepository = deviceInfoRepository,
                settingsRepository = settingsRepository,
            ) as T
        }
    }
}
