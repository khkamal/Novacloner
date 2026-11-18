package com.example

import android.app.Application
import com.example.data.local.NovaSpaceDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.DeviceInfoRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.SecretZoneRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository

class AppContainer(application: Application) {
    private val database: NovaSpaceDatabase by lazy {
        NovaSpaceDatabase.getInstance(application)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(application)
    }

    val workspaceRepository: WorkspaceRepository by lazy {
        WorkspaceRepository(database.workspaceDao())
    }

    val secretZoneRepository: SecretZoneRepository by lazy {
        SecretZoneRepository(
            secretRecordDao = database.secretRecordDao(),
            preferencesManager = preferencesManager,
        )
    }

    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(application)
    }

    val deviceInfoRepository: DeviceInfoRepository by lazy {
        DeviceInfoRepository(
            context = application,
            preferencesManager = preferencesManager,
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            preferencesManager = preferencesManager,
            workspaceRepository = workspaceRepository,
            secretZoneRepository = secretZoneRepository,
        )
    }
}

class NovaSpaceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
