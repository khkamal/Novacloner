package com.example.ui.screens.settings

import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.ThemeMode
import com.example.ui.theme.NovaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenSecretZone: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenDeviceInfo: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetSecretConfirm by remember { mutableStateOf(false) }

    val biometricSupported = remember(context) {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
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
                // Appearance
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
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = prefs.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                    modifier = Modifier.testTag("theme_mode_${mode.key}"),
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }

                // Default Workspace Behavior
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
                            text = "Default Workspace Behavior",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        DefaultWorkspaceBehavior.entries.forEach { behavior ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setDefaultWorkspaceBehavior(behavior) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = prefs.defaultWorkspaceBehavior == behavior,
                                    onClick = { viewModel.setDefaultWorkspaceBehavior(behavior) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = behavior.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = behavior.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                }
                            }
                        }
                    }
                }

                // Secret Zone Settings
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
                        Text("Secret Zone", style = MaterialTheme.typography.titleMedium)

                        SettingsNavigationRow(
                            icon = Icons.Outlined.Shield,
                            title = if (prefs.isSecretPinConfigured) "Open Secret Zone" else "Set Up Secret Zone PIN",
                            subtitle = if (prefs.isSecretPinConfigured) {
                                "PIN configured locally with PBKDF2"
                            } else {
                                "Protect private entries with a 4–8 digit PIN"
                            },
                            onClick = onOpenSecretZone,
                            testTag = "settings_open_secret_zone",
                        )

                        HorizontalDivider(color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-lock in background",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Automatically lock Secret Zone when Nova Space moves to background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovaTheme.colors.secondaryText,
                                )
                            }
                            Switch(
                                checked = prefs.secretAutoLockEnabled,
                                onCheckedChange = viewModel::setSecretAutoLockEnabled,
                                modifier = Modifier.testTag("settings_autolock_switch"),
                            )
                        }

                        if (biometricSupported && prefs.isSecretPinConfigured) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Biometric unlock",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "Allow device biometrics to unlock Secret Zone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                }
                                Switch(
                                    checked = prefs.secretBiometricEnabled,
                                    onCheckedChange = viewModel::setSecretBiometricEnabled,
                                    modifier = Modifier.testTag("settings_biometric_switch"),
                                )
                            }
                        }

                        if (prefs.isSecretPinConfigured) {
                            TextButton(
                                onClick = { showResetSecretConfirm = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.testTag("settings_reset_secret_zone"),
                            ) {
                                Text("Reset Secret Zone & Erase Private Entries")
                            }
                        }
                    }
                }

                // Data, Diagnostics & Transparency
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
                            text = "Data & Transparency",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Storage,
                            title = "Data Management",
                            subtitle = "Export, import, or reset local workspace metadata",
                            onClick = onOpenDataManagement,
                            testTag = "settings_data_management",
                        )
                        HorizontalDivider(color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f))
                        SettingsNavigationRow(
                            icon = Icons.Outlined.PermDeviceInformation,
                            title = "Global Device Information",
                            subtitle = "Shared Android system and host app diagnostics",
                            onClick = onOpenDeviceInfo,
                            testTag = "settings_device_info",
                        )
                        HorizontalDivider(color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f))
                        SettingsNavigationRow(
                            icon = Icons.Outlined.PrivacyTip,
                            title = "Privacy & Local Storage",
                            subtitle = "What Nova Space stores locally and permission scope",
                            onClick = onOpenPrivacy,
                            testTag = "settings_privacy",
                        )
                        HorizontalDivider(color = NovaTheme.colors.subtleBorder.copy(alpha = 0.5f))
                        SettingsNavigationRow(
                            icon = Icons.Outlined.Info,
                            title = "About Nova Space",
                            subtitle = "Version ${viewModel.appVersionLabel} • Open-source licenses",
                            onClick = onOpenAbout,
                            testTag = "settings_about",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showResetSecretConfirm) {
        AlertDialog(
            onDismissRequest = { showResetSecretConfirm = false },
            title = { Text("Reset Secret Zone?") },
            text = {
                Text(
                    "This removes your Secret Zone PIN and permanently deletes all private notes and private workspace entries stored locally.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetSecretZone()
                        showResetSecretConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Reset Secret Zone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSecretConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTheme.colors.secondaryText,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = NovaTheme.colors.secondaryText,
        )
    }
}
