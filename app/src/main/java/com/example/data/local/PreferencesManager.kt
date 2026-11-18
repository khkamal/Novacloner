package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

private val Context.novaSpaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nova_space_preferences"
)

data class StoredPinCredential(
    val hashBase64: String,
    val saltBase64: String,
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_WORKSPACE_BEHAVIOR = stringPreferencesKey("default_workspace_behavior")
        val SECRET_PIN_HASH = stringPreferencesKey("secret_pin_hash")
        val SECRET_PIN_SALT = stringPreferencesKey("secret_pin_salt")
        val SECRET_BIOMETRIC_ENABLED = booleanPreferencesKey("secret_biometric_enabled")
        val SECRET_AUTO_LOCK_ENABLED = booleanPreferencesKey("secret_auto_lock_enabled")
        val APP_GENERATED_INSTANCE_ID = stringPreferencesKey("app_generated_instance_id")
    }

    val userPreferences: Flow<UserPreferences> = context.novaSpaceDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val hash = prefs[Keys.SECRET_PIN_HASH]
            val salt = prefs[Keys.SECRET_PIN_SALT]
            UserPreferences(
                themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
                defaultWorkspaceBehavior = DefaultWorkspaceBehavior.fromKey(
                    prefs[Keys.DEFAULT_WORKSPACE_BEHAVIOR]
                ),
                isSecretPinConfigured = !hash.isNullOrBlank() && !salt.isNullOrBlank(),
                secretBiometricEnabled = prefs[Keys.SECRET_BIOMETRIC_ENABLED] ?: false,
                secretAutoLockEnabled = prefs[Keys.SECRET_AUTO_LOCK_ENABLED] ?: true,
                appGeneratedInstanceId = prefs[Keys.APP_GENERATED_INSTANCE_ID].orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.key
        }
    }

    suspend fun setDefaultWorkspaceBehavior(behavior: DefaultWorkspaceBehavior) {
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_WORKSPACE_BEHAVIOR] = behavior.key
        }
    }

    suspend fun saveSecretPinCredential(hashBase64: String, saltBase64: String) {
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.SECRET_PIN_HASH] = hashBase64
            prefs[Keys.SECRET_PIN_SALT] = saltBase64
        }
    }

    suspend fun getSecretPinCredential(): StoredPinCredential? {
        val prefs = context.novaSpaceDataStore.data.first()
        val hash = prefs[Keys.SECRET_PIN_HASH]
        val salt = prefs[Keys.SECRET_PIN_SALT]
        return if (!hash.isNullOrBlank() && !salt.isNullOrBlank()) {
            StoredPinCredential(hashBase64 = hash, saltBase64 = salt)
        } else {
            null
        }
    }

    suspend fun setSecretBiometricEnabled(enabled: Boolean) {
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.SECRET_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setSecretAutoLockEnabled(enabled: Boolean) {
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.SECRET_AUTO_LOCK_ENABLED] = enabled
        }
    }

    suspend fun clearSecretPinAndSettings() {
        context.novaSpaceDataStore.edit { prefs ->
            prefs.remove(Keys.SECRET_PIN_HASH)
            prefs.remove(Keys.SECRET_PIN_SALT)
            prefs[Keys.SECRET_BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun getOrCreateAppGeneratedInstanceId(): String {
        val current = context.novaSpaceDataStore.data.first()[Keys.APP_GENERATED_INSTANCE_ID]
        if (!current.isNullOrBlank()) {
            return current
        }
        val generated = UUID.randomUUID().toString()
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.APP_GENERATED_INSTANCE_ID] = generated
        }
        return generated
    }

    suspend fun regenerateAppGeneratedInstanceId(): String {
        val generated = UUID.randomUUID().toString()
        context.novaSpaceDataStore.edit { prefs ->
            prefs[Keys.APP_GENERATED_INSTANCE_ID] = generated
        }
        return generated
    }
}
