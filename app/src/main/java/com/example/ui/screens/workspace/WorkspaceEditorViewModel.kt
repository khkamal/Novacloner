package com.example.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val existingWorkspace: Workspace? = null,
    val name: String = "",
    val description: String = "",
    val notes: String = "",
    val accent: WorkspaceAccent = WorkspaceAccent.COBALT,
    val isPinned: Boolean = false,
    val packageName: String? = null,
    val appDisplayName: String? = null,
    val nameError: String? = null,
)

sealed interface WorkspaceEditorEvent {
    data class Saved(val workspaceId: Long) : WorkspaceEditorEvent
}

class WorkspaceEditorViewModel(
    private val workspaceId: Long?,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceEditorUiState(isNew = workspaceId == null))
    val uiState: StateFlow<WorkspaceEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WorkspaceEditorEvent>()
    val events = _events.asSharedFlow()

    private var lastDbUpdatedAt: Long = 0L

    init {
        if (workspaceId == null) {
            _uiState.update { it.copy(isLoading = false, isNew = true) }
        } else {
            loadWorkspace(workspaceId)
        }
    }

    fun reloadLinkedAppFromDb() {
        val id = workspaceId ?: return
        viewModelScope.launch {
            val ws = workspaceRepository.getWorkspace(id) ?: return@launch
            if (ws.updatedAt > lastDbUpdatedAt) {
                lastDbUpdatedAt = ws.updatedAt
                _uiState.update {
                    it.copy(
                        existingWorkspace = ws,
                        packageName = ws.packageName,
                        appDisplayName = ws.appDisplayName,
                    )
                }
            }
        }
    }

    private fun loadWorkspace(id: Long) {
        viewModelScope.launch {
            val ws = workspaceRepository.getWorkspace(id)
            if (ws == null) {
                _uiState.update { it.copy(isLoading = false) }
            } else {
                lastDbUpdatedAt = ws.updatedAt
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isNew = false,
                        existingWorkspace = ws,
                        name = ws.name,
                        description = ws.description,
                        notes = ws.notes,
                        accent = ws.accentColor,
                        isPinned = ws.isPinned,
                        packageName = ws.packageName,
                        appDisplayName = ws.appDisplayName,
                    )
                }
            }
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, nameError = null) }
    }

    fun updateDescription(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateAccent(accent: WorkspaceAccent) {
        _uiState.update { it.copy(accent = accent) }
    }

    fun updatePinned(pinned: Boolean) {
        _uiState.update { it.copy(isPinned = pinned) }
    }

    fun clearAppLink() {
        _uiState.update { it.copy(packageName = null, appDisplayName = null) }
    }

    fun save() {
        val state = _uiState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(nameError = "Please enter a workspace name.") }
            return
        }

        viewModelScope.launch {
            val savedId = if (state.isNew || state.existingWorkspace == null) {
                workspaceRepository.createWorkspace(
                    name = trimmedName,
                    packageName = state.packageName,
                    appDisplayName = state.appDisplayName,
                    description = state.description,
                    notes = state.notes,
                    accentColor = state.accent,
                    isPinned = state.isPinned,
                )
            } else {
                workspaceRepository.updateWorkspace(
                    state.existingWorkspace.copy(
                        name = trimmedName,
                        description = state.description,
                        notes = state.notes,
                        accentColor = state.accent,
                        isPinned = state.isPinned,
                        packageName = state.packageName,
                        appDisplayName = state.appDisplayName,
                    )
                )
                state.existingWorkspace.id
            }
            _events.emit(WorkspaceEditorEvent.Saved(savedId))
        }
    }

    class Factory(
        private val workspaceId: Long?,
        private val workspaceRepository: WorkspaceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkspaceEditorViewModel(workspaceId, workspaceRepository) as T
        }
    }
}
