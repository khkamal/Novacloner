package com.example.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.SecretZoneRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.data.runtime.ContainerRuntimeRegistry
import com.example.domain.model.AppAvailability
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent
import com.example.domain.runtime.RuntimeLaunchResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkspaceCardUiModel(
    val workspace: Workspace,
    val availability: AppAvailability,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val workspaces: List<WorkspaceCardUiModel> = emptyList(),
    val totalWorkspaceCount: Int = 0,
    val linkedWorkspaceCount: Int = 0,
    val searchQuery: String = "",
    val isSecretPinConfigured: Boolean = false,
    val isSecretZoneUnlocked: Boolean = false,
    val defaultWorkspaceBehavior: DefaultWorkspaceBehavior = DefaultWorkspaceBehavior.OPEN_DETAILS,
)

sealed interface HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent
    data class OpenWorkspaceDetails(val workspaceId: Long) : HomeEvent
}

class HomeViewModel(
    private val workspaceRepository: WorkspaceRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val secretZoneRepository: SecretZoneRepository,
    private val runtimeRegistry: ContainerRuntimeRegistry,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val availabilityMapFlow = MutableStateFlow<Map<Long, AppAvailability>>(emptyMap())

    private val _events = MutableSharedFlow<HomeEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        workspaceRepository.observeWorkspaces(),
        searchQueryFlow,
        availabilityMapFlow,
        secretZoneRepository.isUnlocked,
        settingsRepository.userPreferences,
    ) { workspaces, query, availabilityMap, isUnlocked, prefs ->
        val filtered = if (query.isBlank()) {
            workspaces
        } else {
            val q = query.trim().lowercase()
            workspaces.filter { ws ->
                ws.name.lowercase().contains(q) ||
                    (ws.appDisplayName?.lowercase()?.contains(q) == true) ||
                    (ws.packageName?.lowercase()?.contains(q) == true) ||
                    ws.description.lowercase().contains(q) ||
                    ws.notes.lowercase().contains(q)
            }
        }

        val models = filtered.map { ws ->
            val availability = availabilityMap[ws.id] ?: when {
                ws.packageName.isNullOrBlank() -> AppAvailability.NO_APP_LINKED
                else -> AppAvailability.AVAILABLE
            }
            WorkspaceCardUiModel(workspace = ws, availability = availability)
        }

        HomeUiState(
            isLoading = false,
            workspaces = models,
            totalWorkspaceCount = workspaces.size,
            linkedWorkspaceCount = workspaces.count { !it.packageName.isNullOrBlank() },
            searchQuery = query,
            isSecretPinConfigured = prefs.isSecretPinConfigured,
            isSecretZoneUnlocked = isUnlocked,
            defaultWorkspaceBehavior = prefs.defaultWorkspaceBehavior,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            workspaceRepository.observeWorkspaces().collect { list ->
                refreshAvailability(list)
            }
        }
    }

    private suspend fun refreshAvailability(workspaces: List<Workspace>) {
        val updated = HashMap<Long, AppAvailability>(workspaces.size)
        for (ws in workspaces) {
            val pkg = ws.packageName
            updated[ws.id] = if (pkg.isNullOrBlank()) {
                AppAvailability.NO_APP_LINKED
            } else if (installedAppsRepository.isAppInstalled(pkg)) {
                AppAvailability.AVAILABLE
            } else {
                AppAvailability.UNAVAILABLE
            }
        }
        availabilityMapFlow.value = updated
    }

    fun refreshInstalledAppStatuses() {
        viewModelScope.launch {
            val current = uiState.value.workspaces.map { it.workspace }
            refreshAvailability(current)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun createWorkspace(name: String, description: String, accent: WorkspaceAccent) {
        viewModelScope.launch {
            workspaceRepository.createWorkspace(
                name = name,
                description = description,
                accentColor = accent,
            )
            _events.emit(HomeEvent.ShowMessage("Workspace \"$name\" created."))
        }
    }

    fun renameWorkspace(id: Long, newName: String) {
        viewModelScope.launch {
            workspaceRepository.renameWorkspace(id, newName)
            _events.emit(HomeEvent.ShowMessage("Workspace renamed to \"$newName\"."))
        }
    }

    fun togglePin(id: Long) {
        viewModelScope.launch {
            workspaceRepository.togglePin(id)
        }
    }

    fun resetWorkspace(id: Long, clearAppAssociation: Boolean) {
        viewModelScope.launch {
            workspaceRepository.resetWorkspace(id, clearAppAssociation)
            _events.emit(HomeEvent.ShowMessage("Workspace metadata and ID reset."))
        }
    }

    fun deleteWorkspace(id: Long) {
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(id)
            _events.emit(HomeEvent.ShowMessage("Workspace removed."))
        }
    }

    fun onWorkspaceCardClicked(context: Context, item: WorkspaceCardUiModel) {
        viewModelScope.launch {
            val behavior = uiState.value.defaultWorkspaceBehavior
            if (behavior == DefaultWorkspaceBehavior.LAUNCH_LINKED_APP &&
                !item.workspace.packageName.isNullOrBlank()
            ) {
                launchAssociatedApp(context, item.workspace)
            } else {
                _events.emit(HomeEvent.OpenWorkspaceDetails(item.workspace.id))
            }
        }
    }

    fun launchAssociatedApp(context: Context, workspace: Workspace) {
        viewModelScope.launch {
            when (val result = runtimeRegistry.launchWorkspaceApp(context, workspace)) {
                is RuntimeLaunchResult.Success -> Unit
                is RuntimeLaunchResult.Blocked -> {
                    refreshInstalledAppStatuses()
                    _events.emit(HomeEvent.ShowMessage(result.reason))
                }
                is RuntimeLaunchResult.Failure -> {
                    refreshInstalledAppStatuses()
                    _events.emit(HomeEvent.ShowMessage(result.reason))
                }
            }
        }
    }

    class Factory(
        private val workspaceRepository: WorkspaceRepository,
        private val installedAppsRepository: InstalledAppsRepository,
        private val secretZoneRepository: SecretZoneRepository,
        private val runtimeRegistry: ContainerRuntimeRegistry,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                workspaceRepository = workspaceRepository,
                installedAppsRepository = installedAppsRepository,
                secretZoneRepository = secretZoneRepository,
                runtimeRegistry = runtimeRegistry,
                settingsRepository = settingsRepository,
            ) as T
        }
    }
}
