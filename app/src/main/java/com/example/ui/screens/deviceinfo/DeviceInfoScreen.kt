package com.example.ui.screens.deviceinfo

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.NovaTheme
import com.example.utils.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    viewModel: DeviceInfoViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Global Device Info", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Host Android OS & application diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.secondaryText,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("device_info_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh device info",
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

            uiState.errorMessage != null -> {
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
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.snapshot != null -> {
                val info = uiState.snapshot!!
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
                        // Global Scope Clarification
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = NovaTheme.colors.elevatedSurface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Global Host Device Diagnostics",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "This screen reports shared physical device metrics for Nova Space globally. Each Workspace maintains its own independent generated Workspace ID and metadata, accessible from that Workspace's detail screen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.colors.secondaryText,
                                    )
                                }
                            }
                        }

                        InfoSectionCard(
                            title = "System & Device",
                            rows = listOf(
                                "Android Version" to "Android ${info.androidVersion}",
                                "SDK Level" to "API ${info.sdkLevel}",
                                "Manufacturer" to info.manufacturer,
                                "Model" to info.model,
                                "Device Code" to info.deviceCode,
                            ),
                        )

                        InfoSectionCard(
                            title = "Display, Locale & Storage",
                            rows = listOf(
                                "Screen Dimensions" to "${info.screenWidthPx} × ${info.screenHeightPx} px",
                                "Screen Density" to "${info.densityDpi} dpi",
                                "Locale" to info.localeDisplay,
                                "Time Zone" to info.timeZoneDisplay,
                                "Internal Free" to Formatters.formatBytes(info.availableStorageBytes),
                                "Internal Total" to Formatters.formatBytes(info.totalStorageBytes),
                            ),
                        )

                        InfoSectionCard(
                            title = "Nova Space Application",
                            rows = listOf(
                                "App Version" to "${info.appVersionName} (${info.appVersionCode})",
                                "Package ID" to info.packageName,
                            ),
                        )

                        InfoSectionCard(
                            title = "Restricted Hardware Identifiers",
                            rows = listOf(
                                "IMEI / MEID" to "Restricted by Android",
                                "SIM Serial" to "Restricted by Android",
                                "Wi-Fi / BT MAC" to "Restricted by Android",
                                "Hardware Serial" to "Restricted by Android",
                            ),
                        )

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                            modifier = Modifier.fillMaxWidth(),
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
                                    text = "Nova Space only reads standard public Android APIs and never attempts to bypass privacy boundaries, spoof hardware IDs, or call hidden system APIs.",
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
}

@Composable
private fun InfoSectionCard(
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.colors.secondaryText,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }
    }
}
