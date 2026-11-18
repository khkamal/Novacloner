package com.example.ui.screens.importapps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.AppFilterMode
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportAppsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val apps: List<InstalledAppInfo> = emptyList(),
    val totalDiscoveredCount: Int = 0,
    val searchQuery: String = "",
    val filterMode: AppFilterMode = AppFilterMode.ALL,
    val existingWorkspaces: List<Workspace> = emptyList(),
)

sealed interface ImportAppsEvent {
    data class AssignedToWorkspace(val appName: String) : ImportAppsEvent
    data class CreatedWorkspaceWithApp(val workspaceId: Long, val workspaceName: String) : ImportAppsEvent
}

class ImportAppsViewModel(
    private val installedAppsRepository: InstalledAppsRepository,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val rawAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val isLoadingFlow = MutableStateFlow(true)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val searchQueryFlow = MutableStateFlow("")
    private val filterModeFlow = MutableStateFlow(AppFilterMode.ALL)

    private val _events = MutableSharedFlow<ImportAppsEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ImportAppsUiState> = combine(
        isLoadingFlow,
        errorFlow,
        rawAppsFlow,
        searchQueryFlow,
        filterModeFlow,
        workspaceRepository.observeWorkspaces(),
    ) { values ->
        val isLoading = values[0] as Boolean
        val error = values[1] as String?
        @Suppress("UNCHECKED_CAST")
        val allApps = values[2] as List<InstalledAppInfo>
        val query = values[3] as String
        val filter = values[4] as AppFilterMode
        @Suppress("UNCHECKED_CAST")
        val workspaces = values[5] as List<Workspace>

        val byMode = when (filter) {
            AppFilterMode.ALL -> allApps
            AppFilterMode.USER -> allApps.filter { !it.isSystemApp }
            AppFilterMode.SYSTEM -> allApps.filter { it.isSystemApp }
        }

        val filtered = if (query.isBlank()) {
            byMode
        } else {
            val q = query.trim().lowercase()
            byMode.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        ImportAppsUiState(
            isLoading = isLoading,
            errorMessage = error,
            apps = filtered,
            totalDiscoveredCount = allApps.size,
            searchQuery = query,
            filterMode = filter,
            existingWorkspaces = workspaces,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImportAppsUiState(),
    )

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoadingFlow.value = true
            errorFlow.value = null
            runCatching { installedAppsRepository.loadLaunchableApps() }
                .onSuccess { list ->
                    rawAppsFlow.value = list
                    isLoadingFlow.value = false
                }
                .onFailure { throwable ->
                    errorFlow.value = throwable.localizedMessage
                        ?: "Unable to query installed launchable applications."
                    isLoadingFlow.value = false
                }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun updateFilterMode(mode: AppFilterMode) {
        filterModeFlow.value = mode
    }

    fun assignAppToWorkspace(workspaceId: Long, app: InstalledAppInfo) {
        viewModelScope.launch {
            workspaceRepository.associateApp(
                id = workspaceId,
                packageName = app.packageName,
                appDisplayName = app.appName,
            )
            _events.emit(ImportAppsEvent.AssignedToWorkspace(app.appName))
        }
    }

    fun createWorkspaceWithApp(
        name: String,
        description: String,
        accent: WorkspaceAccent,
        app: InstalledAppInfo,
    ) {
        viewModelScope.launch {
            val id = workspaceRepository.createWorkspace(
                name = name,
                packageName = app.packageName,
                appDisplayName = app.appName,
                description = description,
                accentColor = accent,
            )
            _events.emit(ImportAppsEvent.CreatedWorkspaceWithApp(id, name))
        }
    }

    class Factory(
        private val installedAppsRepository: InstalledAppsRepository,
        private val workspaceRepository: WorkspaceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportAppsViewModel(installedAppsRepository, workspaceRepository) as T
        }
    }
}
