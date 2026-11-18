package com.example.ui.screens.secret

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.SecretRecord
import com.example.domain.model.SecretRecordCategory
import com.example.ui.components.AppIconView
import com.example.ui.theme.NovaTheme
import com.example.utils.AppLaunchHelper
import com.example.utils.Formatters
import com.example.utils.LaunchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretZoneScreen(
    viewModel: SecretZoneViewModel,
    installedAppsRepository: InstalledAppsRepository,
    onNavigateBack: () -> Unit,
    onLocked: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<SecretRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<SecretRecord?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadLaunchableAppsIfNeeded()
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (!uiState.isUnlocked) {
            onLocked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SecretZoneEvent.Locked -> onLocked()
                is SecretZoneEvent.Info -> snackbarHostState.showSnackbar(event.message)
                is SecretZoneEvent.Error -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Secret Zone", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (uiState.autoLockEnabled) "Auto-lock enabled" else "Manual lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.success,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("secret_zone_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showChangePinDialog = true },
                        modifier = Modifier.testTag("secret_change_pin_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "Change PIN",
                        )
                    }
                    TextButton(
                        onClick = viewModel::lockNow,
                        modifier = Modifier.testTag("secret_lock_now_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingRecord = null
                    showEditorDialog = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Entry") },
                modifier = Modifier.testTag("secret_add_entry_fab"),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = NovaTheme.colors.elevatedSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Private records stay inside Nova Space's local database behind your PIN. This is an app-level private space, not an OS user profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.colors.secondaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search private notes & workspaces…") },
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
                    .testTag("secret_search_input"),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.records.isEmpty()) {
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
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp),
                        )
                        Text(
                            text = if (uiState.searchQuery.isBlank()) {
                                "Your Secret Zone is empty"
                            } else {
                                "No matching private entries"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Store private notes, confidential checklists, or private workspace shortcuts protected by your local PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.secondaryText,
                        )
                        if (uiState.searchQuery.isBlank()) {
                            Button(
                                onClick = {
                                    editingRecord = null
                                    showEditorDialog = true
                                }
                            ) {
                                Text("Add Private Entry")
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Text("Clear Search")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.records, key = { it.id }) { record ->
                        SecretRecordCard(
                            record = record,
                            installedAppsRepository = installedAppsRepository,
                            onEdit = {
                                editingRecord = record
                                showEditorDialog = true
                            },
                            onDelete = { deletingRecord = record },
                            onLaunchLinkedApp = { pkg ->
                                val result = AppLaunchHelper.launchApp(context, pkg)
                                if (result != LaunchResult.Success) {
                                    viewModel.reportLaunchError(AppLaunchHelper.messageForFailure(result))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        SecretRecordEditorDialog(
            initialRecord = editingRecord,
            launchableApps = uiState.launchableApps,
            onDismiss = { showEditorDialog = false },
            onSave = { title, category, content, linkedApp ->
                viewModel.saveRecord(
                    id = editingRecord?.id ?: 0L,
                    title = title,
                    category = category,
                    content = content,
                    linkedApp = linkedApp,
                )
                showEditorDialog = false
            },
        )
    }

    deletingRecord?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = { Text("This private entry will be permanently removed from Secret Zone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecord(target.id)
                        deletingRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onConfirm = { currentPin, newPin, confirmPin ->
                viewModel.changePin(currentPin, newPin, confirmPin)
                showChangePinDialog = false
            },
        )
    }
}

@Composable
private fun SecretRecordCard(
    record: SecretRecord,
    installedAppsRepository: InstalledAppsRepository,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLaunchLinkedApp: (String) -> Unit,
) {
    Surface(
        onClick = onEdit,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("secret_record_${record.id}"),
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
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = record.category.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit entry",
                            tint = NovaTheme.colors.secondaryText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Text(
                text = record.title,
                style = MaterialTheme.typography.titleMedium,
            )

            if (record.content.isNotBlank()) {
                Text(
                    text = record.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTheme.colors.secondaryText,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!record.linkedPackageName.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = NovaTheme.colors.elevatedSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchLinkedApp(record.linkedPackageName) },
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconView(
                            packageName = record.linkedPackageName,
                            appName = record.linkedAppName,
                            installedAppsRepository = installedAppsRepository,
                            size = 32.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = record.linkedAppName ?: record.linkedPackageName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Launch linked app",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Text(
                text = "Updated ${Formatters.formatTimestamp(record.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = NovaTheme.colors.secondaryText.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SecretRecordEditorDialog(
    initialRecord: SecretRecord?,
    launchableApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: SecretRecordCategory,
        content: String,
        linkedApp: InstalledAppInfo?,
    ) -> Unit,
) {
    var title by remember(initialRecord) { mutableStateOf(initialRecord?.title.orEmpty()) }
    var category by remember(initialRecord) {
        mutableStateOf(initialRecord?.category ?: SecretRecordCategory.PRIVATE_NOTE)
    }
    var content by remember(initialRecord) { mutableStateOf(initialRecord?.content.orEmpty()) }
    var selectedApp by remember(initialRecord, launchableApps) {
        mutableStateOf(
            initialRecord?.linkedPackageName?.let { pkg ->
                launchableApps.firstOrNull { it.packageName == pkg }
                    ?: InstalledAppInfo(
                        packageName = pkg,
                        appName = initialRecord.linkedAppName ?: pkg,
                        versionName = "",
                        isSystemApp = false,
                    )
            }
        )
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRecord == null) "New Private Entry" else "Edit Private Entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = titleError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("secret_entry_title_input"),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecretRecordCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = {
                                Text(
                                    text = when (cat) {
                                        SecretRecordCategory.PRIVATE_NOTE -> "Note"
                                        SecretRecordCategory.PRIVATE_WORKSPACE -> "Workspace"
                                        SecretRecordCategory.CREDENTIAL_REF -> "Ref"
                                    }
                                )
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Private content / notes") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("secret_entry_content_input"),
                )

                if (category == SecretRecordCategory.PRIVATE_WORKSPACE && launchableApps.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = selectedApp?.appName ?: "Optional app shortcut",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.secondaryText,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showAppPicker = true }) {
                            Text(if (selectedApp == null) "Link App" else "Change")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        titleError = true
                    } else {
                        onSave(trimmed, category, content, selectedApp)
                    }
                },
                modifier = Modifier.testTag("secret_entry_save_button"),
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

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Link App to Private Workspace") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    item {
                        TextButton(
                            onClick = {
                                selectedApp = null
                                showAppPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("None (Unlink)")
                        }
                    }
                    items(launchableApps, key = { it.packageName }) { app ->
                        Surface(
                            onClick = {
                                selectedApp = app
                                showAppPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${app.appName} (${app.packageName})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String, confirmNewPin: String) -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Secret Zone PIN") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            currentPin = it
                            localError = null
                        }
                    },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            newPin = it
                            localError = null
                        }
                    },
                    label = { Text("New PIN (4–8 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            confirmPin = it
                            localError = null
                        }
                    },
                    label = { Text("Confirm new PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (localError != null) {
                    Text(
                        text = localError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        currentPin.length < 4 -> localError = "Enter your current PIN."
                        newPin.length !in 4..8 -> localError = "New PIN must be 4 to 8 digits."
                        newPin != confirmPin -> localError = "New PIN confirmation does not match."
                        else -> onConfirm(currentPin, newPin, confirmPin)
                    }
                }
            ) {
                Text("Update PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
