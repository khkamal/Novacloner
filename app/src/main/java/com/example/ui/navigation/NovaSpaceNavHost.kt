package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.ui.screens.deviceinfo.DeviceInfoScreen
import com.example.ui.screens.deviceinfo.DeviceInfoViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.importapps.ImportAppsScreen
import com.example.ui.screens.importapps.ImportAppsViewModel
import com.example.ui.screens.secret.SecretZoneLockScreen
import com.example.ui.screens.secret.SecretZoneScreen
import com.example.ui.screens.secret.SecretZoneSetupScreen
import com.example.ui.screens.secret.SecretZoneViewModel
import com.example.ui.screens.settings.AboutScreen
import com.example.ui.screens.settings.DataManagementScreen
import com.example.ui.screens.settings.PrivacyScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.workspace.WorkspaceDetailScreen
import com.example.ui.screens.workspace.WorkspaceDetailViewModel
import com.example.ui.screens.workspace.WorkspaceDeviceInfoScreen
import com.example.ui.screens.workspace.WorkspaceDeviceInfoViewModel
import com.example.ui.screens.workspace.WorkspaceEditorScreen
import com.example.ui.screens.workspace.WorkspaceEditorViewModel

@Composable
fun NovaSpaceNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSecretUnlocked by container.secretZoneRepository.isUnlocked
        .collectAsStateWithLifecycle()

    fun navigateToSecretZoneGate() {
        scope.launch {
            val pinConfigured = container.secretZoneRepository.isPinConfigured()
            when {
                !pinConfigured -> navController.navigate(SecretZoneSetupRoute) {
                    launchSingleTop = true
                }
                isSecretUnlocked -> navController.navigate(SecretZoneRoute) {
                    launchSingleTop = true
                }
                else -> navController.navigate(SecretZoneLockRoute) {
                    launchSingleTop = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(180)) },
        ) {
            composable<HomeRoute> {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        workspaceRepository = container.workspaceRepository,
                        installedAppsRepository = container.installedAppsRepository,
                        secretZoneRepository = container.secretZoneRepository,
                        runtimeRegistry = container.runtimeRegistry,
                        settingsRepository = container.settingsRepository,
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    installedAppsRepository = container.installedAppsRepository,
                    onOpenSecretZone = { navigateToSecretZoneGate() },
                    onOpenImportApps = { targetId ->
                        navController.navigate(ImportAppsRoute(targetWorkspaceId = targetId))
                    },
                    onOpenWorkspaceDetail = { id ->
                        navController.navigate(WorkspaceDetailRoute(workspaceId = id))
                    },
                    onOpenWorkspaceEditor = { id ->
                        navController.navigate(WorkspaceEditorRoute(workspaceId = id))
                    },
                    onOpenDeviceInfo = {
                        navController.navigate(DeviceInfoRoute) { launchSingleTop = true }
                    },
                    onOpenSettings = {
                        navController.navigate(SettingsRoute) { launchSingleTop = true }
                    },
                )
            }

            composable<ImportAppsRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ImportAppsRoute>()
                val vm: ImportAppsViewModel = viewModel(
                    factory = ImportAppsViewModel.Factory(
                        installedAppsRepository = container.installedAppsRepository,
                        workspaceRepository = container.workspaceRepository,
                    )
                )
                ImportAppsScreen(
                    targetWorkspaceId = route.targetWorkspaceId,
                    viewModel = vm,
                    installedAppsRepository = container.installedAppsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onAppAssigned = { navController.popBackStack() },
                )
            }

            composable<WorkspaceDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<WorkspaceDetailRoute>()
                val vm: WorkspaceDetailViewModel = viewModel(
                    factory = WorkspaceDetailViewModel.Factory(
                        workspaceId = route.workspaceId,
                        workspaceRepository = container.workspaceRepository,
                        installedAppsRepository = container.installedAppsRepository,
                        runtimeRegistry = container.runtimeRegistry,
                        profileSandboxManager = container.profileSandboxManager,
                    )
                )
                WorkspaceDetailScreen(
                    viewModel = vm,
                    installedAppsRepository = container.installedAppsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onChangeApp = { wsId ->
                        navController.navigate(ImportAppsRoute(targetWorkspaceId = wsId))
                    },
                    onEditWorkspace = { wsId ->
                        navController.navigate(WorkspaceEditorRoute(workspaceId = wsId))
                    },
                    onOpenWorkspaceDeviceInfo = { wsId ->
                        navController.navigate(WorkspaceDeviceInfoRoute(workspaceId = wsId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<WorkspaceDeviceInfoRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<WorkspaceDeviceInfoRoute>()
                val vm: WorkspaceDeviceInfoViewModel = viewModel(
                    factory = WorkspaceDeviceInfoViewModel.Factory(
                        workspaceId = route.workspaceId,
                        workspaceRepository = container.workspaceRepository,
                        installedAppsRepository = container.installedAppsRepository,
                        deviceInfoRepository = container.deviceInfoRepository,
                        settingsRepository = container.settingsRepository,
                    )
                )
                WorkspaceDeviceInfoScreen(
                    viewModel = vm,
                    installedAppsRepository = container.installedAppsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenGlobalDeviceInfo = {
                        navController.navigate(DeviceInfoRoute) { launchSingleTop = true }
                    },
                )
            }

            composable<WorkspaceEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<WorkspaceEditorRoute>()
                val vm: WorkspaceEditorViewModel = viewModel(
                    factory = WorkspaceEditorViewModel.Factory(
                        workspaceId = route.workspaceId,
                        workspaceRepository = container.workspaceRepository,
                    )
                )
                WorkspaceEditorScreen(
                    viewModel = vm,
                    installedAppsRepository = container.installedAppsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onSelectAppForWorkspace = { wsId ->
                        navController.navigate(ImportAppsRoute(targetWorkspaceId = wsId))
                    },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable<SecretZoneSetupRoute> {
                val vm: SecretZoneViewModel = viewModel(
                    factory = SecretZoneViewModel.Factory(
                        secretZoneRepository = container.secretZoneRepository,
                        settingsRepository = container.settingsRepository,
                        installedAppsRepository = container.installedAppsRepository,
                    )
                )
                SecretZoneSetupScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onSetupSuccess = {
                        navController.navigate(SecretZoneRoute) {
                            popUpTo(SecretZoneSetupRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<SecretZoneLockRoute> {
                val vm: SecretZoneViewModel = viewModel(
                    factory = SecretZoneViewModel.Factory(
                        secretZoneRepository = container.secretZoneRepository,
                        settingsRepository = container.settingsRepository,
                        installedAppsRepository = container.installedAppsRepository,
                    )
                )
                SecretZoneLockScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onUnlocked = {
                        navController.navigate(SecretZoneRoute) {
                            popUpTo(SecretZoneLockRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onResetToHome = {
                        navController.popBackStack()
                    },
                )
            }

            composable<SecretZoneRoute> {
                val vm: SecretZoneViewModel = viewModel(
                    factory = SecretZoneViewModel.Factory(
                        secretZoneRepository = container.secretZoneRepository,
                        settingsRepository = container.settingsRepository,
                        installedAppsRepository = container.installedAppsRepository,
                    )
                )
                SecretZoneScreen(
                    viewModel = vm,
                    installedAppsRepository = container.installedAppsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onLocked = {
                        navController.navigate(SecretZoneLockRoute) {
                            popUpTo(SecretZoneRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<DeviceInfoRoute> {
                val vm: DeviceInfoViewModel = viewModel(
                    factory = DeviceInfoViewModel.Factory(container.deviceInfoRepository)
                )
                DeviceInfoScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<SettingsRoute> {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        settingsRepository = container.settingsRepository,
                        workspaceRepository = container.workspaceRepository,
                        secretZoneRepository = container.secretZoneRepository,
                        context = context,
                    )
                )
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSecretZone = { navigateToSecretZoneGate() },
                    onOpenDataManagement = {
                        navController.navigate(DataManagementRoute) { launchSingleTop = true }
                    },
                    onOpenDeviceInfo = {
                        navController.navigate(DeviceInfoRoute) { launchSingleTop = true }
                    },
                    onOpenPrivacy = {
                        navController.navigate(PrivacyRoute) { launchSingleTop = true }
                    },
                    onOpenAbout = {
                        navController.navigate(AboutRoute) { launchSingleTop = true }
                    },
                )
            }

            composable<DataManagementRoute> {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        settingsRepository = container.settingsRepository,
                        workspaceRepository = container.workspaceRepository,
                        secretZoneRepository = container.secretZoneRepository,
                        context = context,
                    )
                )
                DataManagementScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<AboutRoute> {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        settingsRepository = container.settingsRepository,
                        workspaceRepository = container.workspaceRepository,
                        secretZoneRepository = container.secretZoneRepository,
                        context = context,
                    )
                )
                AboutScreen(
                    appVersionLabel = vm.appVersionLabel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<PrivacyRoute> {
                PrivacyScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
