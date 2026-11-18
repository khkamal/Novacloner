package com.example.ui.screens.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.AppAvailability
import com.example.ui.components.AppIconView
import com.example.ui.theme.NovaMonoLabelStyle
import com.example.ui.theme.NovaTheme
import com.example.utils.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDeviceInfoScreen(
    viewModel: WorkspaceDeviceInfoViewModel,
    installedAppsRepository: InstalledAppsRepository,
    onNavigateBack: () -> Unit,
    onOpenGlobalDeviceInfo: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRegenerateDialog by remember { mutableStateOf(false) }

    fun copyToClipboard(clipLabel: String, value: String, feedback: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(clipLabel, value))
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(feedback)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkspaceDeviceInfoEvent.ShowMessage -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(event.message)
                }
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
                    Column {
                        Text(
                            text = "Workspace Device Info",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (workspace != null) {
                            Text(
                                text = workspace.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = NovaTheme.colors.secondaryText,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("workspace_device_info_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh workspace info",
                        )
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
                            Text("Go Back")
                        }
                    }
                }
            }

            else -> {
                val identityFields = listOf(
                    IdentityFieldItem(
                        label = "Device ID",
                        value = workspace.simulatedDeviceId,
                        valueTag = "identity_device_id_value",
                        copyTag = "copy_device_id_button",
                    ),
                    IdentityFieldItem(
                        label = "Android ID",
                        value = workspace.simulatedAndroidId,
                        valueTag = "identity_android_id_value",
                        copyTag = "copy_android_id_button",
                    ),
                    IdentityFieldItem(
                        label = "WiFi MAC",
                        value = workspace.simulatedWifiMac,
                        valueTag = "identity_wifi_mac_value",
                        copyTag = "copy_wifi_mac_button",
                    ),
                    IdentityFieldItem(
                        label = "Bluetooth MAC",
                        value = workspace.simulatedBluetoothMac,
                        valueTag = "identity_bluetooth_mac_value",
                        copyTag = "copy_bluetooth_mac_button",
                    ),
                    IdentityFieldItem(
                        label = "ICC Serial",
                        value = workspace.simulatedIccSerial,
                        valueTag = "identity_icc_serial_value",
                        copyTag = "copy_icc_serial_button",
                    ),
                    IdentityFieldItem(
                        label = "Workspace UUID",
                        value = workspace.generatedWorkspaceId,
                        valueTag = "workspace_device_info_uuid",
                        copyTag = "workspace_copy_uuid_button",
                    ),
                )

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
                        // Structured Workspace Device Identity Panel
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = NovaTheme.colors.elevatedSurface,
                            border = BorderStroke(
                                1.dp,
                                workspace.accentColor.color.copy(alpha = 0.45f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("device_identity_panel"),
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Fingerprint,
                                                contentDescription = null,
                                                tint = workspace.accentColor.color,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = "Device Identity",
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            ),
                                        ) {
                                            Text(
                                                text = "Simulated Profile",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 4.dp,
                                                ),
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Simulated per-Workspace identity values stored locally for \"${workspace.name}\". They do not alter real Android hardware identifiers or provide spoofed values to external installed apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )

                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            NovaTheme.colors.subtleBorder.copy(alpha = 0.9f),
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            identityFields.forEachIndexed { index, item ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        color = NovaTheme.colors.subtleBorder.copy(alpha = 0.55f)
                                                    )
                                                }
                                                DeviceIdentityRow(
                                                    label = item.label,
                                                    value = item.value,
                                                    valueTag = item.valueTag,
                                                    copyTag = item.copyTag,
                                                    onCopy = {
                                                        copyToClipboard(
                                                            clipLabel = "${workspace.name} ${item.label}",
                                                            value = item.value,
                                                            feedback = "${item.label} copied.",
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val formattedSummary = identityFields.joinToString("\n") {
                                                    "${it.label}: ${it.value}"
                                                }
                                                copyToClipboard(
                                                    clipLabel = "${workspace.name} Device Identity",
                                                    value = formattedSummary,
                                                    feedback = "Device identity profile copied.",
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("workspace_copy_all_identity_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy All")
                                        }
                                        FilledTonalButton(
                                            onClick = { showRegenerateDialog = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("workspace_regenerate_uuid_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Regenerate")
                                        }
                                    }
                                }
                            }
                        }

                        // Workspace Lifecycle Summary
                        WorkspaceInfoSectionCard(
                            title = "Workspace Lifecycle",
                            rows = listOf(
                                "Workspace Name" to workspace.name,
                                "Local Record Key" to "#${workspace.id}",
                                "Creation Date" to Formatters.formatTimestamp(workspace.createdAt),
                                "Last Updated" to Formatters.formatTimestamp(workspace.updatedAt),
                                "Launch Count" to "${workspace.launchCount}",
                                "Last Launched" to (
                                    workspace.lastLaunchedAt?.let { Formatters.formatTimestamp(it) }
                                        ?: "Never"
                                    ),
                            ),
                        )

                        // Linked Application Information
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
                                    text = "Linked Application",
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                if (workspace.packageName.isNullOrBlank()) {
                                    Text(
                                        text = "No application linked to this Workspace.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppIconView(
                                            packageName = workspace.packageName,
                                            appName = workspace.appDisplayName,
                                            installedAppsRepository = installedAppsRepository,
                                            size = 44.dp,
                                            accentColor = workspace.accentColor.color,
                                            isUnavailable = uiState.availability == AppAvailability.UNAVAILABLE,
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = uiState.installedAppDetails?.appName
                                                    ?: workspace.appDisplayName
                                                    ?: workspace.packageName,
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                text = workspace.packageName,
                                                style = NovaMonoLabelStyle,
                                                color = NovaTheme.colors.secondaryText,
                                                modifier = Modifier.testTag("workspace_device_info_linked_package"),
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f)
                                    )

                                    val statusLabel = when (uiState.availability) {
                                        AppAvailability.AVAILABLE -> "Installed & launchable"
                                        AppAvailability.UNAVAILABLE -> "Not currently installed on device"
                                        AppAvailability.NO_APP_LINKED -> "No application linked"
                                    }
                                    WorkspaceInfoRow("Availability", statusLabel)

                                    uiState.installedAppDetails?.let { details ->
                                        HorizontalDivider(
                                            color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f)
                                        )
                                        WorkspaceInfoRow("App Version", details.versionName)
                                        HorizontalDivider(
                                            color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f)
                                        )
                                        WorkspaceInfoRow(
                                            "Package Scope",
                                            if (details.isSystemApp) "System application" else "User-installed application"
                                        )
                                    }
                                }
                            }
                        }

                        // Workspace-Specific Settings & Metadata
                        WorkspaceInfoSectionCard(
                            title = "Workspace Settings & Metadata",
                            rows = listOf(
                                "Accent Theme" to workspace.accentColor.label,
                                "Pinned to Top" to if (workspace.isPinned) "Pinned" else "No",
                                "Description" to workspace.description.ifBlank { "None" },
                                "Local Notes" to if (workspace.notes.isBlank()) {
                                    "None recorded"
                                } else {
                                    "${workspace.notes.length} characters recorded"
                                },
                                "Card Tap Behavior" to uiState.defaultCardBehavior.label,
                            ),
                        )

                        // Global Host Device Context (Clearly Separated)
                        uiState.hostDeviceSnapshot?.let { host ->
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
                                        text = "Global Host Device (Shared)",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "Shared device metrics apply to the physical Android OS across all Workspaces.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                    WorkspaceInfoRow(
                                        "Android OS",
                                        "Android ${host.androidVersion} (API ${host.sdkLevel})"
                                    )
                                    HorizontalDivider(
                                        color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f)
                                    )
                                    WorkspaceInfoRow("Device", "${host.manufacturer} ${host.model}")
                                    HorizontalDivider(
                                        color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f)
                                    )
                                    WorkspaceInfoRow("Host Package", host.packageName)
                                    HorizontalDivider(
                                        color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f)
                                    )
                                    WorkspaceInfoRow(
                                        "Hardware IDs (IMEI / MAC / Serial)",
                                        "Restricted by Android"
                                    )

                                    OutlinedButton(
                                        onClick = onOpenGlobalDeviceInfo,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("open_global_device_info_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PermDeviceInformation,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View Global Device Information")
                                    }
                                }
                            }
                        }

                        // Scope honesty footer
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workspace_device_info_footer"),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = NovaTheme.colors.success,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Workspace identity profiles are simulated values stored solely inside Nova Space's local Room database. Deleting, resetting, or regenerating this Workspace affects only its local profile and never modifies external apps or system hardware identifiers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovaTheme.colors.secondaryText,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (workspace != null && showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("Regenerate Device Identity?") },
            text = {
                Text(
                    "This generates new simulated Device ID, Android ID, WiFi MAC, Bluetooth MAC, ICC Serial, and Workspace UUID values exclusively for \"${workspace.name}\". Other Workspaces, installed applications, and real Android hardware identifiers are unaffected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.regenerateWorkspaceIdentity()
                        showRegenerateDialog = false
                    },
                    modifier = Modifier.testTag("confirm_workspace_regenerate_uuid_button"),
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private data class IdentityFieldItem(
    val label: String,
    val value: String,
    val valueTag: String,
    val copyTag: String,
)

@Composable
private fun DeviceIdentityRow(
    label: String,
    value: String,
    valueTag: String,
    copyTag: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NovaTheme.colors.secondaryText,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = NovaMonoLabelStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .testTag(valueTag),
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.testTag(copyTag),
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy $label",
                tint = NovaTheme.colors.secondaryText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun WorkspaceInfoSectionCard(
    title: String,
    rows: List<Pair<String, String>>,
) {
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f))
                }
                WorkspaceInfoRow(label = label, value = value)
            }
        }
    }
}

@Composable
private fun WorkspaceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NovaTheme.colors.secondaryText,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
        )
    }
}
