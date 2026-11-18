package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val preferencesManager: PreferencesManager,
    private val workspaceRepository: WorkspaceRepository,
    private val secretZoneRepository: SecretZoneRepository,
) {
    val userPreferences: Flow<UserPreferences> = preferencesManager.userPreferences

    suspend fun setThemeMode(mode: ThemeMode) {
        preferencesManager.setThemeMode(mode)
    }

    suspend fun setDefaultWorkspaceBehavior(behavior: DefaultWorkspaceBehavior) {
        preferencesManager.setDefaultWorkspaceBehavior(behavior)
    }

    suspend fun setSecretBiometricEnabled(enabled: Boolean) {
        preferencesManager.setSecretBiometricEnabled(enabled)
    }

    suspend fun setSecretAutoLockEnabled(enabled: Boolean) {
        preferencesManager.setSecretAutoLockEnabled(enabled)
    }

    suspend fun resetAllApplicationData(clearSecretZone: Boolean) {
        workspaceRepository.deleteAllWorkspaces()
        if (clearSecretZone) {
            secretZoneRepository.resetSecretZone()
        }
    }
}
