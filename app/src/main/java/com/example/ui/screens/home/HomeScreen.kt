package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.Workspace
import com.example.ui.components.CreateWorkspaceDialog
import com.example.ui.components.DeleteWorkspaceDialog
import com.example.ui.components.RenameWorkspaceDialog
import com.example.ui.components.ResetWorkspaceDialog
import com.example.ui.components.WorkspaceCard
import com.example.ui.theme.NovaTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    installedAppsRepository: InstalledAppsRepository,
    onOpenSecretZone: () -> Unit,
    onOpenImportApps: (targetWorkspaceId: Long?) -> Unit,
    onOpenWorkspaceDetail: (workspaceId: Long) -> Unit,
    onOpenWorkspaceEditor: (workspaceId: Long?) -> Unit,
    onOpenDeviceInfo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var resettingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var deletingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshInstalledAppStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is HomeEvent.OpenWorkspaceDetails -> onOpenWorkspaceDetail(event.workspaceId)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            HomeTopHeader(
                totalWorkspaces = uiState.totalWorkspaceCount,
                linkedCount = uiState.linkedWorkspaceCount,
                showSearchButton = uiState.totalWorkspaceCount > 0,
                isSearchActive = showSearchBar,
                onToggleSearch = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) viewModel.updateSearchQuery("")
                },
                onOpenImportApps = { onOpenImportApps(null) },
                onOpenDeviceInfo = onOpenDeviceInfo,
                onOpenSettings = onOpenSettings,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_workspace)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_workspace_fab"),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = if (maxWidth >= 680.dp) 2 else 1

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (showSearchBar && uiState.totalWorkspaceCount > 0) {
                        item(span = { GridItemSpan(columns) }) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text("Search workspaces, apps, or notes…") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Clear search",
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_search_input"),
                            )
                        }
                    }

                    // Secret Zone Hero Card
                    item(span = { GridItemSpan(columns) }) {
                        SecretZoneCard(
                            isPinConfigured = uiState.isSecretPinConfigured,
                            isUnlocked = uiState.isSecretZoneUnlocked,
                            onClick = onOpenSecretZone,
                        )
                    }

                    // Section header
                    item(span = { GridItemSpan(columns) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Workspaces",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onOpenImportApps(null) },
                                    modifier = Modifier.testTag("home_import_apps_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import Apps")
                                }
                            }
                        }
                    }

                    if (uiState.totalWorkspaceCount == 0) {
                        item(span = { GridItemSpan(columns) }) {
                            HomeEmptyWorkspaceState(
                                onAddWorkspace = { showCreateDialog = true },
                                onImportApps = { onOpenImportApps(null) },
                            )
                        }
                    } else if (uiState.workspaces.isEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "No workspaces match \"${uiState.searchQuery}\"",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    TextButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Text("Clear Filter")
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = uiState.workspaces,
                            key = { it.workspace.id },
                        ) { item ->
                            WorkspaceCard(
                                workspace = item.workspace,
                                availability = item.availability,
                                installedAppsRepository = installedAppsRepository,
                                onCardClick = { viewModel.onWorkspaceCardClicked(context, item) },
                                onOpenDetails = { onOpenWorkspaceDetail(item.workspace.id) },
                                onAddOrChangeApp = { onOpenImportApps(item.workspace.id) },
                                onLaunchApp = {
                                    viewModel.launchAssociatedApp(context, item.workspace)
                                },
                                onRename = { renamingWorkspace = item.workspace },
                                onEditDetails = { onOpenWorkspaceEditor(item.workspace.id) },
                                onTogglePin = { viewModel.togglePin(item.workspace.id) },
                                onResetWorkspace = { resettingWorkspace = item.workspace },
                                onDeleteWorkspace = { deletingWorkspace = item.workspace },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateWorkspaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, accent ->
                viewModel.createWorkspace(name, desc, accent)
                showCreateDialog = false
            },
        )
    }

    renamingWorkspace?.let { target ->
        RenameWorkspaceDialog(
            currentName = target.name,
            onDismiss = { renamingWorkspace = null },
            onConfirm = { newName ->
                viewModel.renameWorkspace(target.id, newName)
                renamingWorkspace = null
            },
        )
    }

    resettingWorkspace?.let { target ->
        ResetWorkspaceDialog(
            workspaceName = target.name,
            hasLinkedApp = !target.packageName.isNullOrBlank(),
            onDismiss = { resettingWorkspace = null },
            onConfirm = { clearApp ->
                viewModel.resetWorkspace(target.id, clearApp)
                resettingWorkspace = null
            },
        )
    }

    deletingWorkspace?.let { target ->
        DeleteWorkspaceDialog(
            workspaceName = target.name,
            onDismiss = { deletingWorkspace = null },
            onConfirm = {
                viewModel.deleteWorkspace(target.id)
                deletingWorkspace = null
            },
        )
    }
}

@Composable
private fun HomeTopHeader(
    totalWorkspaces: Int,
    linkedCount: Int,
    showSearchButton: Boolean,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onOpenImportApps: () -> Unit,
    onOpenDeviceInfo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_launcher_emblem),
                    contentDescription = "Nova Space logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "$totalWorkspaces workspaces • $linkedCount linked",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.colors.secondaryText,
                    )
                }

                if (showSearchButton) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.testTag("home_search_toggle"),
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search workspaces",
                            tint = if (isSearchActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                NovaTheme.colors.secondaryText
                            },
                        )
                    }
                }

                IconButton(
                    onClick = onOpenImportApps,
                    modifier = Modifier.testTag("home_top_import_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = "Import installed apps",
                        tint = NovaTheme.colors.secondaryText,
                    )
                }

                IconButton(
                    onClick = onOpenDeviceInfo,
                    modifier = Modifier.testTag("home_device_info_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PermDeviceInformation,
                        contentDescription = "Device information",
                        tint = NovaTheme.colors.secondaryText,
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = NovaTheme.colors.secondaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun SecretZoneCard(
    isPinConfigured: Boolean,
    isUnlocked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("secret_zone_card"),
        shape = MaterialTheme.shapes.large,
        color = NovaTheme.colors.elevatedSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        !isPinConfigured -> Icons.Outlined.Shield
                        isUnlocked -> Icons.Outlined.LockOpen
                        else -> Icons.Outlined.Lock
                    },
                    contentDescription = "Secret Zone security status",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.secret_zone_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.secret_zone_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.colors.secondaryText,
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = when {
                    !isPinConfigured -> MaterialTheme.colorScheme.primaryContainer
                    isUnlocked -> NovaTheme.colors.successContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            ) {
                Text(
                    text = when {
                        !isPinConfigured -> "Set Up PIN"
                        isUnlocked -> "Unlocked"
                        else -> "PIN Locked"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !isPinConfigured -> MaterialTheme.colorScheme.primary
                        isUnlocked -> NovaTheme.colors.success
                        else -> NovaTheme.colors.secondaryText
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = NovaTheme.colors.secondaryText,
            )
        }
    }
}

@Composable
private fun HomeEmptyWorkspaceState(
    onAddWorkspace: () -> Unit,
    onImportApps: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_empty_state"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_workspace),
                contentDescription = "Workspace cards illustration",
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.empty_workspace_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.empty_workspace_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTheme.colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onAddWorkspace,
                    modifier = Modifier.testTag("empty_add_workspace_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_workspace))
                }

                OutlinedButton(
                    onClick = onImportApps,
                    modifier = Modifier.testTag("empty_import_apps_button"),
                ) {
                    Text(stringResource(R.string.import_apps))
                }
            }
        }
    }
}
