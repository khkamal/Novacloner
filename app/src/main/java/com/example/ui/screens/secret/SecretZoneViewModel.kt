package com.example.ui.screens.secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.SecretZoneRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.SecretRecord
import com.example.domain.model.SecretRecordCategory
import com.example.utils.PinCryptoHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecretZoneUiState(
    val isPinConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockEnabled: Boolean = true,
    val records: List<SecretRecord> = emptyList(),
    val searchQuery: String = "",
    val launchableApps: List<InstalledAppInfo> = emptyList(),
)

sealed interface SecretZoneEvent {
    data object SetupCompleted : SecretZoneEvent
    data object Unlocked : SecretZoneEvent
    data object Locked : SecretZoneEvent
    data object ZoneReset : SecretZoneEvent
    data class Error(val message: String) : SecretZoneEvent
    data class Info(val message: String) : SecretZoneEvent
}

class SecretZoneViewModel(
    private val secretZoneRepository: SecretZoneRepository,
    private val settingsRepository: SettingsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val appsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())

    private val _events = MutableSharedFlow<SecretZoneEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<SecretZoneUiState> = combine(
        settingsRepository.userPreferences,
        secretZoneRepository.isUnlocked,
        secretZoneRepository.observeRecords(),
        searchQueryFlow,
        appsFlow,
    ) { prefs, isUnlocked, records, query, apps ->
        val filtered = if (query.isBlank()) {
            records
        } else {
            val q = query.trim().lowercase()
            records.filter {
                it.title.lowercase().contains(q) ||
                    it.content.lowercase().contains(q) ||
                    (it.linkedAppName?.lowercase()?.contains(q) == true)
            }
        }
        SecretZoneUiState(
            isPinConfigured = prefs.isSecretPinConfigured,
            isUnlocked = isUnlocked,
            biometricEnabled = prefs.secretBiometricEnabled,
            autoLockEnabled = prefs.secretAutoLockEnabled,
            records = filtered,
            searchQuery = query,
            launchableApps = apps,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecretZoneUiState(),
    )

    fun loadLaunchableAppsIfNeeded() {
        if (appsFlow.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { installedAppsRepository.loadLaunchableApps() }
                .onSuccess { appsFlow.value = it }
        }
    }

    fun setupPin(pin: String, confirmPin: String, enableBiometric: Boolean) {
        viewModelScope.launch {
            if (!PinCryptoHelper.isValidPinFormat(pin)) {
                _events.emit(SecretZoneEvent.Error("PIN must be 4 to 8 digits."))
                return@launch
            }
            if (pin != confirmPin) {
                _events.emit(SecretZoneEvent.Error("PIN confirmation does not match."))
                return@launch
            }
            val ok = secretZoneRepository.setupPin(pin, enableBiometric)
            if (ok) {
                _events.emit(SecretZoneEvent.SetupCompleted)
            } else {
                _events.emit(SecretZoneEvent.Error("Unable to store derived PIN credential."))
            }
        }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            if (pin.isBlank()) {
                _events.emit(SecretZoneEvent.Error("Enter your Secret Zone PIN."))
                return@launch
            }
            val matched = secretZoneRepository.verifyPinAndUnlock(pin)
            if (matched) {
                _events.emit(SecretZoneEvent.Unlocked)
            } else {
                _events.emit(SecretZoneEvent.Error("Incorrect PIN. Please try again."))
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            secretZoneRepository.unlockViaBiometric()
            _events.emit(SecretZoneEvent.Unlocked)
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmNewPin: String) {
        viewModelScope.launch {
            if (!PinCryptoHelper.isValidPinFormat(newPin)) {
                _events.emit(SecretZoneEvent.Error("New PIN must be 4 to 8 digits."))
                return@launch
            }
            if (newPin != confirmNewPin) {
                _events.emit(SecretZoneEvent.Error("New PIN confirmation does not match."))
                return@launch
            }
            val changed = secretZoneRepository.changePin(currentPin, newPin)
            if (changed) {
                _events.emit(SecretZoneEvent.Info("Secret Zone PIN updated."))
            } else {
                _events.emit(SecretZoneEvent.Error("Current PIN is incorrect."))
            }
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            secretZoneRepository.lock()
            _events.emit(SecretZoneEvent.Locked)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun saveRecord(
        id: Long = 0L,
        title: String,
        category: SecretRecordCategory,
        content: String,
        linkedApp: InstalledAppInfo? = null,
    ) {
        viewModelScope.launch {
            secretZoneRepository.saveRecord(
                id = id,
                title = title,
                category = category,
                content = content,
                linkedPackageName = linkedApp?.packageName,
                linkedAppName = linkedApp?.appName,
            )
            _events.emit(SecretZoneEvent.Info("Private entry saved."))
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            secretZoneRepository.deleteRecord(id)
            _events.emit(SecretZoneEvent.Info("Private entry deleted."))
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSecretBiometricEnabled(enabled)
        }
    }

    fun setAutoLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSecretAutoLockEnabled(enabled)
        }
    }

    fun resetSecretZone() {
        viewModelScope.launch {
            secretZoneRepository.resetSecretZone()
            _events.emit(SecretZoneEvent.ZoneReset)
        }
    }

    fun reportLaunchError(message: String) {
        viewModelScope.launch {
            _events.emit(SecretZoneEvent.Error(message))
        }
    }

    class Factory(
        private val secretZoneRepository: SecretZoneRepository,
        private val settingsRepository: SettingsRepository,
        private val installedAppsRepository: InstalledAppsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SecretZoneViewModel(
                secretZoneRepository = secretZoneRepository,
                settingsRepository = settingsRepository,
                installedAppsRepository = installedAppsRepository,
            ) as T
        }
    }
}
