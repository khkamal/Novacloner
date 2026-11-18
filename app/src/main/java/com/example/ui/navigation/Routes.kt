package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ImportAppsRoute(val targetWorkspaceId: Long? = null)

@Serializable
data class WorkspaceDetailRoute(val workspaceId: Long)

@Serializable
data class WorkspaceEditorRoute(val workspaceId: Long? = null)

@Serializable
data object SecretZoneSetupRoute

@Serializable
data object SecretZoneLockRoute

@Serializable
data object SecretZoneRoute

@Serializable
data object DeviceInfoRoute

@Serializable
data object SettingsRoute

@Serializable
data object DataManagementRoute

@Serializable
data object AboutRoute

@Serializable
data object PrivacyRoute
