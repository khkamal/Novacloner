package com.example.ui.screens.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.AppAvailability
import com.example.domain.model.CompatibilityStatus
import com.example.domain.model.Workspace
import com.example.domain.runtime.ResolvedRuntimeState
import com.example.ui.components.AppIconView
import com.example.ui.components.DeleteWorkspaceDialog
import com.example.ui.components.RenameWorkspaceDialog
import com.example.ui.components.ResetWorkspaceDialog
import com.example.ui.components.WorkspaceStatusBadge
import com.example.ui.theme.NovaMonoLabelStyle
import com.example.ui.theme.NovaTheme
import com.example.utils.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailScreen(
    viewModel: WorkspaceDetailViewModel,
    installedAppsRepository: InstalledAppsRepository,
    onNavigateBack: () -> Unit,
    onChangeApp: (workspaceId: Long) -> Unit,
    onEditWorkspace: (workspaceId: Long) -> Unit,
    onOpenWorkspaceDeviceInfo: (workspaceId: Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showRenameDialog by remember { mutableStateOf(false) }
    var showEditNotesDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkspaceDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                WorkspaceDetailEvent.WorkspaceDeleted -> onNavigateBack()
            }
        }
    }

    val workspace = uiState.workspace

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workspace?.name ?: "Workspace",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    if (workspace != null) {
                        IconButton(
                            onClick = viewModel::togglePin,
                            modifier = Modifier.testTag("detail_pin_button"),
                        ) {
                            Icon(
                                imageVector = if (workspace.isPinned) {
                                    Icons.Filled.PushPin
                                } else {
                                    Icons.Outlined.PushPin
                                },
                                contentDescription = if (workspace.isPinned) "Unpin" else "Pin",
                                tint = if (workspace.isPinned) {
                                    workspace.accentColor.color
                                } else {
                                    NovaTheme.colors.secondaryText
                                },
                            )
                        }
                        IconButton(
                            onClick = { onEditWorkspace(workspace.id) },
                            modifier = Modifier.testTag("detail_edit_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit workspace details",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            workspace == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Workspace not found",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = onNavigateBack) {
                            Text("Return Home")
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Header Identity Card
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(workspace.accentColor.color)
                                )
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        WorkspaceStatusBadge(
                                            availability = uiState.availability,
                                            updatedAt = workspace.updatedAt,
                                        )
                                        TextButton(
                                            onClick = { showRenameDialog = true },
                                            modifier = Modifier.testTag("detail_rename_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DriveFileRenameOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Rename")
                                        }
                                    }

                                    Text(
                                        text = workspace.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    if (workspace.description.isNotBlank()) {
                                        Text(
                                            text = workspace.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NovaTheme.colors.secondaryText,
                                        )
                                    }
                                }
                            }
                        }

                        // Associated Application Panel
                        AssociatedAppSection(
                            workspace = workspace,
                            availability = uiState.availability,
                            installedAppsRepository = installedAppsRepository,
                            onLaunchApp = { viewModel.launchAssociatedApp(context) },
                            onChangeApp = { onChangeApp(workspace.id) },
                            onUnlinkApp = viewModel::unlinkApp,
                        )

                        // Execution Mode & Runtime Status Panel
                        uiState.runtimeState?.let { runtimeState ->
                            ExecutionRuntimeSection(runtimeState = runtimeState)
                        }

                        // Notes Panel
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Workspace Notes",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    TextButton(
                                        onClick = { showEditNotesDialog = true },
                                        modifier = Modifier.testTag("detail_edit_notes_button"),
                                    ) {
                                        Text(if (workspace.notes.isBlank()) "Add Notes" else "Edit Notes")
                                    }
                                }

                                if (workspace.notes.isBlank()) {
                                    Text(
                                        text = "No notes recorded yet. Keep workflow reminders, account context labels, or session checklists here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                } else {
                                    Text(
                                        text = workspace.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // Metadata & Honest Architecture Note
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Workspace Metadata",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                MetadataLine("Workspace ID", workspace.generatedWorkspaceId)
                                MetadataLine("Created", Formatters.formatTimestamp(workspace.createdAt))
                                MetadataLine("Last Updated", Formatters.formatTimestamp(workspace.updatedAt))
                                MetadataLine("Launch Count", "${workspace.launchCount}")
                                MetadataLine(
                                    "Last Launched",
                                    workspace.lastLaunchedAt?.let { Formatters.formatTimestamp(it) }
                                        ?: "Never"
                                )
                                FilledTonalButton(
                                    onClick = { onOpenWorkspaceDeviceInfo(workspace.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .testTag("detail_device_info_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PermDeviceInformation,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Workspace Device Info")
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Nova Space organizes workspace metadata locally and launches the installed Android app directly through PackageManager. It does not create an OS-level virtual device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                }
                            }
                        }

                        // Destructive & Maintenance Actions
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Manage Workspace",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { showResetDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("detail_reset_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.RestartAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reset Workspace")
                                    }

                                    OutlinedButton(
                                        onClick = { showDeleteDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("detail_delete_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (workspace != null && showRenameDialog) {
        RenameWorkspaceDialog(
            currentName = workspace.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameWorkspace(newName)
                showRenameDialog = false
            },
        )
    }

    if (workspace != null && showEditNotesDialog) {
        EditNotesDialog(
            initialNotes = workspace.notes,
            onDismiss = { showEditNotesDialog = false },
            onSave = { notes ->
                viewModel.saveNotes(notes)
                showEditNotesDialog = false
            },
        )
    }

    if (workspace != null && showResetDialog) {
        ResetWorkspaceDialog(
            workspaceName = workspace.name,
            hasLinkedApp = !workspace.packageName.isNullOrBlank(),
            onDismiss = { showResetDialog = false },
            onConfirm = { clearApp ->
                viewModel.resetWorkspace(clearApp)
                showResetDialog = false
            },
        )
    }

    if (workspace != null && showDeleteDialog) {
        DeleteWorkspaceDialog(
            workspaceName = workspace.name,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteWorkspace()
            },
        )
    }
}

@Composable
private fun AssociatedAppSection(
    workspace: Workspace,
    availability: AppAvailability,
    installedAppsRepository: InstalledAppsRepository,
    onLaunchApp: () -> Unit,
    onChangeApp: () -> Unit,
    onUnlinkApp: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Associated Application",
                style = MaterialTheme.typography.titleMedium,
            )

            if (workspace.packageName.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = NovaTheme.colors.elevatedSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No application linked yet",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Choose any launchable Android app installed on this device to pair with this workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.secondaryText,
                        )
                        Button(
                            onClick = onChangeApp,
                            modifier = Modifier.testTag("detail_add_app_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add App")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconView(
                        packageName = workspace.packageName,
                        appName = workspace.appDisplayName,
                        installedAppsRepository = installedAppsRepository,
                        size = 52.dp,
                        accentColor = workspace.accentColor.color,
                        isUnavailable = availability == AppAvailability.UNAVAILABLE,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workspace.appDisplayName ?: workspace.packageName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = workspace.packageName,
                            style = NovaMonoLabelStyle,
                            color = NovaTheme.colors.secondaryText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (availability == AppAvailability.AVAILABLE) {
                                "Installed & launchable"
                            } else {
                                "Application not currently installed"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (availability == AppAvailability.AVAILABLE) {
                                NovaTheme.colors.success
                            } else {
                                NovaTheme.colors.warning
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onLaunchApp,
                        enabled = availability == AppAvailability.AVAILABLE,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_launch_app_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Launch App")
                    }

                    FilledTonalButton(
                        onClick = onChangeApp,
                        modifier = Modifier.testTag("detail_change_app_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change")
                    }

                    IconButton(
                        onClick = onUnlinkApp,
                        modifier = Modifier.testTag("detail_unlink_app_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LinkOff,
                            contentDescription = "Unlink application",
                            tint = NovaTheme.colors.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionRuntimeSection(runtimeState: ResolvedRuntimeState) {
    val badgeBg = if (runtimeState.isVirtualized) {
        NovaTheme.colors.successContainer
    } else {
        NovaTheme.colors.elevatedSurface
    }
    val badgeFg = if (runtimeState.isVirtualized) {
        NovaTheme.colors.success
    } else {
        NovaTheme.colors.secondaryText
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_execution_runtime_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Execution & Runtime",
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (runtimeState.isVirtualized) "Isolated" else "Not Virtualized",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                    )
                }
            }

            MetadataLine(
                label = "Execution Mode",
                value = runtimeState.executionModeLabel,
                valueTag = "detail_execution_mode_value",
            )
            MetadataLine(
                label = "Runtime Status",
                value = runtimeState.runtimeStatusLabel,
                valueTag = "detail_runtime_status_value",
            )
            MetadataLine(
                label = "Isolated App Data",
                value = if (runtimeState.providesIsolatedAppData) {
                    "Yes — Isolated sandbox storage"
                } else {
                    "No — Uses shared host app storage"
                },
                valueTag = "detail_isolated_data_value",
            )
            MetadataLine(
                label = "Profile Detection",
                value = runtimeState.workProfileProbe.capabilitySummary,
                valueTag = "detail_work_profile_probe_value",
            )

            Text(
                text = runtimeState.statusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTheme.colors.secondaryText,
            )

            if (runtimeState.limitations.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    text = "Runtime Limitations",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                runtimeState.limitations.forEach { limitation ->
                    Text(
                        text = "• $limitation",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.colors.secondaryText,
                    )
                }
            }

            runtimeState.compatibilityReport?.let { report ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    text = "App Compatibility Analysis",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MetadataLine(
                    label = "Compatibility",
                    value = report.status.label,
                    valueTag = "detail_compatibility_status_value",
                )
                if (report.isInstalled) {
                    val flagsSummary = buildList {
                        if (report.targetSdkVersion > 0) add("API ${report.targetSdkVersion}")
                        add(if (report.isSplitApk) "Split APK" else "Single APK")
                        if (report.isSystemApp) add("System App")
                        if (report.referencesPlayServices) add("GMS Metadata")
                    }.joinToString(" • ")
                    MetadataLine(
                        label = "App Profile",
                        value = flagsSummary,
                    )
                }
                val warnColor = if (report.status == CompatibilityStatus.UNAVAILABLE) {
                    NovaTheme.colors.warning
                } else {
                    NovaTheme.colors.secondaryText
                }
                report.warnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = warnColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(
    label: String,
    value: String,
    valueTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NovaTheme.colors.secondaryText,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(0.62f)
                .then(if (valueTag != null) Modifier.testTag(valueTag) else Modifier),
        )
    }
}

@Composable
private fun EditNotesDialog(
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Workspace Notes") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_notes_input"),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(notes) },
                modifier = Modifier.testTag("save_notes_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
