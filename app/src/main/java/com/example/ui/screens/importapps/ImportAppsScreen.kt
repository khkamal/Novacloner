package com.example.ui.screens.importapps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.AppFilterMode
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.Workspace
import com.example.ui.components.AppIconView
import com.example.ui.components.CreateWorkspaceDialog
import com.example.ui.theme.NovaMonoLabelStyle
import com.example.ui.theme.NovaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportAppsScreen(
    targetWorkspaceId: Long?,
    viewModel: ImportAppsViewModel,
    installedAppsRepository: InstalledAppsRepository,
    onNavigateBack: () -> Unit,
    onAppAssigned: (message: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingAppSelection by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showCreateForApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportAppsEvent.AssignedToWorkspace -> {
                    onAppAssigned("Linked ${event.appName} to workspace.")
                }
                is ImportAppsEvent.CreatedWorkspaceWithApp -> {
                    onAppAssigned("Created workspace \"${event.workspaceName}\".")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (targetWorkspaceId != null) "Select App" else "Import Apps",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "${uiState.totalDiscoveredCount} launchable apps discovered",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.secondaryText,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("import_apps_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::loadApps,
                        modifier = Modifier.testTag("import_apps_refresh_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh installed apps",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Honest scoped-visibility disclosure card
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Associates a launchable Android app with a workspace card using scoped launcher visibility. Nova Space never reads or copies another app's private data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.colors.secondaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search by app name or package…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_apps_search_input"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(AppFilterMode.entries) { mode ->
                    FilterChip(
                        selected = uiState.filterMode == mode,
                        onClick = { viewModel.updateFilterMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Discovering installed launchable apps…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovaTheme.colors.secondaryText,
                            )
                        }
                    }
                }

                uiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "Failed to query apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = viewModel::loadApps) {
                                Text("Retry")
                            }
                        }
                    }
                }

                uiState.apps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Apps,
                                contentDescription = null,
                                tint = NovaTheme.colors.secondaryText,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "No matching launchable applications",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Only apps exposing an Android launcher entry appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovaTheme.colors.secondaryText,
                            )
                            if (uiState.searchQuery.isNotEmpty() || uiState.filterMode != AppFilterMode.ALL) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateSearchQuery("")
                                        viewModel.updateFilterMode(AppFilterMode.ALL)
                                    }
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = uiState.apps,
                            key = { it.packageName },
                        ) { app ->
                            InstalledAppRow(
                                app = app,
                                installedAppsRepository = installedAppsRepository,
                                actionLabel = if (targetWorkspaceId != null) "Select" else "Add",
                                onClick = {
                                    if (targetWorkspaceId != null) {
                                        viewModel.assignAppToWorkspace(targetWorkspaceId, app)
                                    } else {
                                        pendingAppSelection = app
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingAppSelection?.let { selectedApp ->
        AssignTargetWorkspaceDialog(
            app = selectedApp,
            existingWorkspaces = uiState.existingWorkspaces,
            onDismiss = { pendingAppSelection = null },
            onCreateNewWorkspace = {
                pendingAppSelection = null
                showCreateForApp = selectedApp
            },
            onSelectWorkspace = { ws ->
                pendingAppSelection = null
                viewModel.assignAppToWorkspace(ws.id, selectedApp)
            },
        )
    }

    showCreateForApp?.let { app ->
        CreateWorkspaceDialog(
            initialAppName = app.appName,
            onDismiss = { showCreateForApp = null },
            onCreate = { name, desc, accent ->
                viewModel.createWorkspaceWithApp(name, desc, accent, app)
                showCreateForApp = null
            },
        )
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppInfo,
    installedAppsRepository: InstalledAppsRepository,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_item_${app.packageName}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconView(
                packageName = app.packageName,
                appName = app.appName,
                installedAppsRepository = installedAppsRepository,
                size = 44.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = NovaMonoLabelStyle,
                    color = NovaTheme.colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${app.versionName}" + if (app.isSystemApp) " • System" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.colors.secondaryText.copy(alpha = 0.75f),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AssignTargetWorkspaceDialog(
    app: InstalledAppInfo,
    existingWorkspaces: List<Workspace>,
    onDismiss: () -> Unit,
    onCreateNewWorkspace: () -> Unit,
    onSelectWorkspace: (Workspace) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${app.appName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Create a new workspace card linked to this app, or update an existing workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.colors.secondaryText,
                )

                Button(
                    onClick = onCreateNewWorkspace,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Workspace with ${app.appName}")
                }

                if (existingWorkspaces.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Link to existing workspace",
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaTheme.colors.secondaryText,
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    ) {
                        items(existingWorkspaces, key = { it.id }) { ws ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = NovaTheme.colors.elevatedSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectWorkspace(ws) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = ws.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = ws.appDisplayName ?: "Unlinked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
