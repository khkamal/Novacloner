package com.example.ui.screens.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.AppAvailability
import com.example.domain.model.Workspace
import com.example.utils.AppLaunchHelper
import com.example.utils.LaunchResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkspaceDetailUiState(
    val isLoading: Boolean = true,
    val workspace: Workspace? = null,
    val availability: AppAvailability = AppAvailability.NO_APP_LINKED,
)

sealed interface WorkspaceDetailEvent {
    data class ShowMessage(val message: String) : WorkspaceDetailEvent
    data object WorkspaceDeleted : WorkspaceDetailEvent
}

class WorkspaceDetailViewModel(
    private val workspaceId: Long,
    private val workspaceRepository: WorkspaceRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val availabilityFlow = MutableStateFlow(AppAvailability.NO_APP_LINKED)
    private val _events = MutableSharedFlow<WorkspaceDetailEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<WorkspaceDetailUiState> = combine(
        workspaceRepository.observeWorkspace(workspaceId),
        availabilityFlow,
    ) { workspace, availability ->
        WorkspaceDetailUiState(
            isLoading = false,
            workspace = workspace,
            availability = availability,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkspaceDetailUiState(),
    )

    init {
        viewModelScope.launch {
            workspaceRepository.observeWorkspace(workspaceId).collect { ws ->
                refreshAvailability(ws)
            }
        }
    }

    fun refreshAvailability(workspace: Workspace? = uiState.value.workspace) {
        viewModelScope.launch {
            val pkg = workspace?.packageName
            availabilityFlow.value = when {
                pkg.isNullOrBlank() -> AppAvailability.NO_APP_LINKED
                installedAppsRepository.isAppInstalled(pkg) -> AppAvailability.AVAILABLE
                else -> AppAvailability.UNAVAILABLE
            }
        }
    }

    fun launchAssociatedApp(context: Context) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            when (val result = AppLaunchHelper.launchApp(context, ws.packageName)) {
                LaunchResult.Success -> {
                    workspaceRepository.recordAppLaunch(ws.id)
                }
                else -> {
                    refreshAvailability(ws)
                    _events.emit(
                        WorkspaceDetailEvent.ShowMessage(AppLaunchHelper.messageForFailure(result))
                    )
                }
            }
        }
    }

    fun unlinkApp() {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            workspaceRepository.associateApp(ws.id, null, null)
            _events.emit(WorkspaceDetailEvent.ShowMessage("Application association removed."))
        }
    }

    fun renameWorkspace(newName: String) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            workspaceRepository.renameWorkspace(ws.id, newName)
            _events.emit(WorkspaceDetailEvent.ShowMessage("Workspace renamed."))
        }
    }

    fun saveNotes(newNotes: String) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            workspaceRepository.updateWorkspace(ws.copy(notes = newNotes))
            _events.emit(WorkspaceDetailEvent.ShowMessage("Workspace notes saved."))
        }
    }

    fun togglePin() {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            workspaceRepository.togglePin(ws.id)
        }
    }

    fun resetWorkspace(clearAppAssociation: Boolean) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            workspaceRepository.resetWorkspace(ws.id, clearAppAssociation)
            _events.emit(WorkspaceDetailEvent.ShowMessage("Workspace content reset."))
        }
    }

    fun deleteWorkspace() {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(ws.id)
            _events.emit(WorkspaceDetailEvent.WorkspaceDeleted)
        }
    }

    class Factory(
        private val workspaceId: Long,
        private val workspaceRepository: WorkspaceRepository,
        private val installedAppsRepository: InstalledAppsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkspaceDetailViewModel(
                workspaceId = workspaceId,
                workspaceRepository = workspaceRepository,
                installedAppsRepository = installedAppsRepository,
            ) as T
        }
    }
}
