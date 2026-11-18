package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.domain.model.WorkspaceAccent
import com.example.ui.theme.NovaTheme

@Composable
fun CreateWorkspaceDialog(
    initialAppName: String? = null,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, accent: WorkspaceAccent) -> Unit,
) {
    var name by remember {
        mutableStateOf(
            if (!initialAppName.isNullOrBlank()) "$initialAppName Space" else ""
        )
    }
    var description by remember { mutableStateOf("") }
    var selectedAccent by remember { mutableStateOf(WorkspaceAccent.COBALT) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Workspace name") },
                    placeholder = { Text("e.g. Work Profile, Focus Reader") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Enter a workspace name.") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_workspace_name_input"),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Short purpose or context") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_workspace_desc_input"),
                )

                Text(
                    text = "Workspace accent",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaTheme.colors.secondaryText,
                )

                AccentColorPickerRow(
                    selectedAccent = selectedAccent,
                    onSelectAccent = { selectedAccent = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = true
                    } else {
                        onCreate(trimmed, description.trim(), selectedAccent)
                    }
                },
                modifier = Modifier.testTag("confirm_create_workspace_button"),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun RenameWorkspaceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Workspace") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) isError = false
                },
                label = { Text("Workspace name") },
                singleLine = true,
                isError = isError,
                supportingText = if (isError) {
                    { Text("Workspace name cannot be empty.") }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_workspace_input"),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        isError = true
                    } else {
                        onConfirm(trimmed)
                    }
                },
                modifier = Modifier.testTag("confirm_rename_button"),
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

@Composable
fun ResetWorkspaceDialog(
    workspaceName: String,
    hasLinkedApp: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (clearAppAssociation: Boolean) -> Unit,
) {
    var clearAppAssociation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                tint = NovaTheme.colors.warning,
            )
        },
        title = { Text("Reset \"$workspaceName\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This resets only \"$workspaceName\" inside Nova Space: clearing its description, notes, accent color, pinned state, and launch counter, and generating a new Workspace ID. Other Workspaces are unaffected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "It does not reset any external installed app's data, cache, login session, hardware identity, or Android sandbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.colors.secondaryText,
                )
                if (hasLinkedApp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearAppAssociation = !clearAppAssociation }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = clearAppAssociation,
                            onCheckedChange = { clearAppAssociation = it },
                        )
                        Text(
                            text = "Also unlink associated application",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(clearAppAssociation) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaTheme.colors.warning,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.testTag("confirm_reset_workspace_button"),
            ) {
                Text("Reset Workspace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DeleteWorkspaceDialog(
    workspaceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete \"$workspaceName\"?") },
        text = {
            Text(
                text = "This removes the workspace card and its local notes from Nova Space. Any associated Android app on your device remains installed and untouched.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("confirm_delete_workspace_button"),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ResetAllAppDataDialog(
    hasSecretZone: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (includeSecretZone: Boolean) -> Unit,
) {
    var includeSecretZone by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Reset Application Data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This permanently deletes all user-created workspace cards, local notes, and launch history in Nova Space. Other installed apps and Android device identifiers are never affected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hasSecretZone) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeSecretZone = !includeSecretZone }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = includeSecretZone,
                            onCheckedChange = { includeSecretZone = it },
                        )
                        Text(
                            text = "Also wipe Secret Zone PIN & private records",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(includeSecretZone) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("confirm_reset_all_data_button"),
            ) {
                Text("Erase Local Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun AccentColorPickerRow(
    selectedAccent: WorkspaceAccent,
    onSelectAccent: (WorkspaceAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceAccent.entries.forEach { accent ->
            val isSelected = accent == selectedAccent
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelectAccent(accent) }
                    .semantics {
                        contentDescription = "Select ${accent.label} accent color"
                    }
            )
        }
    }
}
