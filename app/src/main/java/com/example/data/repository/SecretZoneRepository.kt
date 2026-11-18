package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.local.SecretRecordDao
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.SecretRecord
import com.example.domain.model.SecretRecordCategory
import com.example.utils.PinCryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SecretZoneRepository(
    private val secretRecordDao: SecretRecordDao,
    private val preferencesManager: PreferencesManager,
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun observeRecords(): Flow<List<SecretRecord>> =
        secretRecordDao.observeAllRecords().map { list -> list.map { it.toDomain() } }

    suspend fun isPinConfigured(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.getSecretPinCredential() != null
    }

    suspend fun setupPin(pin: String, enableBiometric: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!PinCryptoHelper.isValidPinFormat(pin)) return@withContext false
        val derived = PinCryptoHelper.derivePinCredential(pin)
        preferencesManager.saveSecretPinCredential(
            hashBase64 = derived.hashBase64,
            saltBase64 = derived.saltBase64,
        )
        preferencesManager.setSecretBiometricEnabled(enableBiometric)
        _isUnlocked.value = true
        true
    }

    suspend fun verifyPinAndUnlock(pin: String): Boolean = withContext(Dispatchers.IO) {
        val stored = preferencesManager.getSecretPinCredential() ?: return@withContext false
        val matched = PinCryptoHelper.verifyPin(
            pin = pin,
            storedHashBase64 = stored.hashBase64,
            storedSaltBase64 = stored.saltBase64,
        )
        if (matched) {
            _isUnlocked.value = true
        }
        matched
    }

    fun unlockViaBiometric() {
        _isUnlocked.value = true
    }

    suspend fun changePin(currentPin: String, newPin: String): Boolean = withContext(Dispatchers.IO) {
        if (!PinCryptoHelper.isValidPinFormat(newPin)) return@withContext false
        val stored = preferencesManager.getSecretPinCredential() ?: return@withContext false
        val matched = PinCryptoHelper.verifyPin(
            pin = currentPin,
            storedHashBase64 = stored.hashBase64,
            storedSaltBase64 = stored.saltBase64,
        )
        if (!matched) return@withContext false
        val derived = PinCryptoHelper.derivePinCredential(newPin)
        preferencesManager.saveSecretPinCredential(
            hashBase64 = derived.hashBase64,
            saltBase64 = derived.saltBase64,
        )
        true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    suspend fun onAppMovedToBackground() {
        val prefs = preferencesManager.userPreferences.first()
        if (prefs.secretAutoLockEnabled && _isUnlocked.value) {
            _isUnlocked.value = false
        }
    }

    suspend fun saveRecord(
        id: Long = 0L,
        title: String,
        category: SecretRecordCategory,
        content: String,
        linkedPackageName: String? = null,
        linkedAppName: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = if (id > 0L) secretRecordDao.getRecordById(id) else null
        val record = SecretRecord(
            id = existing?.id ?: 0L,
            title = title.trim().ifBlank { "Untitled Private Entry" },
            category = category,
            content = content.trim(),
            linkedPackageName = linkedPackageName?.takeIf { it.isNotBlank() },
            linkedAppName = linkedAppName?.takeIf { it.isNotBlank() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (existing == null) {
            secretRecordDao.insertRecord(record.toEntity())
        } else {
            secretRecordDao.updateRecord(record.toEntity())
            existing.id
        }
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        secretRecordDao.deleteRecordById(id)
    }

    suspend fun resetSecretZone() = withContext(Dispatchers.IO) {
        _isUnlocked.value = false
        secretRecordDao.deleteAllRecords()
        preferencesManager.clearSecretPinAndSettings()
    }
}
