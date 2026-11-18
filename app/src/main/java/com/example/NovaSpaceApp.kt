package com.example

import android.app.Application
import com.example.data.local.NovaSpaceDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.DeviceInfoRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.SecretZoneRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WorkspaceRepository
import com.example.data.runtime.AppCompatibilityAnalyzer
import com.example.data.runtime.ContainerRuntimeRegistry
import com.example.data.runtime.HostDirectRuntime
import com.example.data.runtime.ProfileSandboxManager
import com.example.data.runtime.UnsupportedRuntime
import com.example.data.runtime.WorkProfileDetector
import com.example.data.runtime.WorkProfileRuntime
import com.example.domain.model.ExecutionMode

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
        DeviceInfoRepository(context = application)
    }

    val workProfileDetector: WorkProfileDetector by lazy {
        WorkProfileDetector(application)
    }

    val appCompatibilityAnalyzer: AppCompatibilityAnalyzer by lazy {
        AppCompatibilityAnalyzer(application)
    }

    val profileSandboxManager: ProfileSandboxManager by lazy {
        ProfileSandboxManager(application)
    }

    val hostDirectRuntime: HostDirectRuntime by lazy {
        HostDirectRuntime(application)
    }

    val workProfileRuntime: WorkProfileRuntime by lazy {
        WorkProfileRuntime(
            workProfileDetector = workProfileDetector,
            launchBackend = WorkProfileRuntime.AndroidProfileLaunchBackend(application),
        )
    }

    val runtimeRegistry: ContainerRuntimeRegistry by lazy {
        ContainerRuntimeRegistry(
            hostDirectRuntime = hostDirectRuntime,
            workProfileRuntime = workProfileRuntime,
            isolatedContainerRuntime = UnsupportedRuntime(
                mode = ExecutionMode.ISOLATED_CONTAINER,
                reason = "In-process APK container virtualization is unavailable on non-privileged Android without violating Play Dynamic Code Loading policies.",
            ),
            unsupportedRuntime = UnsupportedRuntime(mode = ExecutionMode.UNSUPPORTED),
            workProfileDetector = workProfileDetector,
            compatibilityAnalyzer = appCompatibilityAnalyzer,
            workspaceRepository = workspaceRepository,
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
