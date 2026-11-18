package com.example.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SecretZoneRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val secretZoneRepository: SecretZoneRepository,
    context: Context,
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = settingsRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences(),
        )

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    val appVersionLabel: String = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName ?: "1.0"} (Build $code)"
    }.getOrDefault("1.0")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setDefaultWorkspaceBehavior(behavior: DefaultWorkspaceBehavior) {
        viewModelScope.launch {
            settingsRepository.setDefaultWorkspaceBehavior(behavior)
        }
    }

    fun setSecretBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSecretBiometricEnabled(enabled)
        }
    }

    fun setSecretAutoLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSecretAutoLockEnabled(enabled)
        }
    }

    fun resetSecretZone() {
        viewModelScope.launch {
            secretZoneRepository.resetSecretZone()
            _events.emit(SettingsEvent.ShowMessage("Secret Zone PIN and private records cleared."))
        }
    }

    suspend fun generateWorkspaceExportJson(): String {
        return workspaceRepository.exportWorkspacesJson()
    }

    fun importWorkspaceMetadataJson(rawJson: String) {
        viewModelScope.launch {
            workspaceRepository.importWorkspacesJson(rawJson)
                .onSuccess { count ->
                    _events.emit(
                        SettingsEvent.ShowMessage("Imported $count workspace record(s).")
                    )
                }
                .onFailure {
                    _events.emit(
                        SettingsEvent.ShowMessage("Invalid workspace metadata JSON file.")
                    )
                }
        }
    }

    fun resetAllApplicationData(includeSecretZone: Boolean) {
        viewModelScope.launch {
            settingsRepository.resetAllApplicationData(clearSecretZone = includeSecretZone)
            _events.emit(SettingsEvent.ShowMessage("Local Nova Space data cleared."))
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val workspaceRepository: WorkspaceRepository,
        private val secretZoneRepository: SecretZoneRepository,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsRepository = settingsRepository,
                workspaceRepository = workspaceRepository,
                secretZoneRepository = secretZoneRepository,
                context = context.applicationContext,
            ) as T
        }
    }
}
