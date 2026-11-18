package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.InstalledAppsRepository
import com.example.domain.model.AppAvailability
import com.example.domain.model.Workspace
import com.example.ui.theme.NovaMonoLabelStyle
import com.example.ui.theme.NovaTheme
import com.example.utils.Formatters

@Composable
fun WorkspaceCard(
    workspace: Workspace,
    availability: AppAvailability,
    installedAppsRepository: InstalledAppsRepository,
    onCardClick: () -> Unit,
    onOpenDetails: () -> Unit,
    onAddOrChangeApp: () -> Unit,
    onLaunchApp: () -> Unit,
    onRename: () -> Unit,
    onEditDetails: () -> Unit,
    onTogglePin: () -> Unit,
    onResetWorkspace: () -> Unit,
    onDeleteWorkspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = workspace.accentColor.color

    Surface(
        onClick = onCardClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("workspace_card_${workspace.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (workspace.isPinned) {
                accent.copy(alpha = 0.45f)
            } else {
                NovaTheme.colors.subtleBorder
            }
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle top accent bar giving each workspace card a distinct visual identity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accent.copy(alpha = 0.85f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconView(
                        packageName = workspace.packageName,
                        appName = workspace.appDisplayName,
                        installedAppsRepository = installedAppsRepository,
                        size = 48.dp,
                        accentColor = accent,
                        isUnavailable = availability == AppAvailability.UNAVAILABLE,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = workspace.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (workspace.isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned workspace",
                                    tint = accent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when (availability) {
                                AppAvailability.AVAILABLE ->
                                    workspace.appDisplayName ?: workspace.packageName.orEmpty()
                                AppAvailability.UNAVAILABLE ->
                                    "${workspace.appDisplayName ?: workspace.packageName} (Unavailable)"
                                AppAvailability.NO_APP_LINKED ->
                                    "No application associated"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (availability == AppAvailability.UNAVAILABLE) {
                                NovaTheme.colors.warning
                            } else {
                                NovaTheme.colors.secondaryText
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("workspace_menu_${workspace.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Workspace actions for ${workspace.name}",
                                tint = NovaTheme.colors.secondaryText,
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Details") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Info, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenDetails()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (workspace.packageName.isNullOrBlank()) "Add App"
                                        else "Change App"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAddOrChangeApp()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.DriveFileRenameOutline,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Details") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEditDetails()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (workspace.isPinned) "Unpin" else "Pin to Top") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.PushPin, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePin()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Reset Workspace Data") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onResetWorkspace()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Delete Workspace",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteWorkspace()
                                }
                            )
                        }
                    }
                }

                if (workspace.description.isNotBlank() || workspace.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = workspace.description.ifBlank { workspace.notes },
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.colors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!workspace.packageName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = workspace.packageName,
                        style = NovaMonoLabelStyle,
                        color = NovaTheme.colors.secondaryText.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    WorkspaceStatusBadge(availability = availability, updatedAt = workspace.updatedAt)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (workspace.packageName.isNullOrBlank() ||
                            availability == AppAvailability.UNAVAILABLE
                        ) {
                            FilledTonalButton(
                                onClick = onAddOrChangeApp,
                                modifier = Modifier.testTag("card_add_app_${workspace.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (availability == AppAvailability.UNAVAILABLE) "Reassign"
                                    else "Add App"
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onAddOrChangeApp,
                                modifier = Modifier.testTag("card_change_app_${workspace.id}"),
                            ) {
                                Text("Add / Swap")
                            }
                            FilledTonalButton(
                                onClick = onLaunchApp,
                                modifier = Modifier.testTag("card_launch_${workspace.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Launch")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceStatusBadge(
    availability: AppAvailability,
    updatedAt: Long,
    modifier: Modifier = Modifier,
) {
    val (dotColor, bgColor, textColor, label) = when (availability) {
        AppAvailability.AVAILABLE -> Quadruple(
            NovaTheme.colors.success,
            NovaTheme.colors.successContainer,
            NovaTheme.colors.success,
            "Ready"
        )
        AppAvailability.UNAVAILABLE -> Quadruple(
            NovaTheme.colors.warning,
            NovaTheme.colors.warningContainer,
            NovaTheme.colors.warning,
            "Missing App"
        )
        AppAvailability.NO_APP_LINKED -> Quadruple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            "Unlinked"
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bgColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
            )
        }
        Text(
            text = Formatters.formatShortDate(updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = NovaTheme.colors.secondaryText,
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
