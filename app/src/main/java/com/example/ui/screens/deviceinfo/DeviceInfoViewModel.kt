package com.example.ui.screens.deviceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceInfoRepository
import com.example.domain.model.DeviceInfoSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceInfoUiState(
    val isLoading: Boolean = true,
    val snapshot: DeviceInfoSnapshot? = null,
    val errorMessage: String? = null,
)

class DeviceInfoViewModel(
    private val deviceInfoRepository: DeviceInfoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { deviceInfoRepository.getDeviceInfoSnapshot() }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(isLoading = false, snapshot = snapshot, errorMessage = null)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Unable to load device info.",
                        )
                    }
                }
        }
    }

    fun regenerateAppWorkspaceId() {
        viewModelScope.launch {
            val newId = deviceInfoRepository.regenerateAppGeneratedWorkspaceId()
            _uiState.update { state ->
                state.copy(
                    snapshot = state.snapshot?.copy(appGeneratedWorkspaceId = newId)
                )
            }
        }
    }

    class Factory(
        private val deviceInfoRepository: DeviceInfoRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceInfoViewModel(deviceInfoRepository) as T
        }
    }
}
