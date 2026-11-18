package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.NovaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    appVersionLabel: String,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("About Nova Space") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("about_back_button"),
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
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_launcher_emblem),
                            contentDescription = "Nova Space emblem",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Nova Space", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Version $appVersionLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovaTheme.colors.secondaryText,
                            )
                            Text(
                                text = "Offline-first Android Workspace Manager",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

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
                        Text("Platform Scope & Honesty", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Nova Space organizes installed Android apps, workspace notes, launch tracking, and a PIN-protected Secret Zone locally on your device using official Android APIs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovaTheme.colors.secondaryText,
                        )
                        Text(
                            text = "Standard Android application sandboxing does not create isolated virtual devices, clone arbitrary APK sandboxes, or modify hardware identifiers. Launching an app hands off directly to the installed app through Android's PackageManager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaTheme.colors.secondaryText,
                        )
                    }
                }

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
                        Text("Open-Source Libraries", style = MaterialTheme.typography.titleMedium)
                        val libs = listOf(
                            "Jetpack Compose & Material 3" to "Apache License 2.0",
                            "AndroidX Room & DataStore" to "Apache License 2.0",
                            "AndroidX Navigation Compose" to "Apache License 2.0",
                            "AndroidX Biometric" to "Apache License 2.0",
                            "Kotlin Coroutines & Serialization" to "Apache License 2.0",
                        )
                        libs.forEachIndexed { index, (name, license) ->
                            if (index > 0) {
                                HorizontalDivider(color = NovaTheme.colors.subtleBorder.copy(alpha = 0.4f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    license,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NovaTheme.colors.secondaryText,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Local Storage") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("privacy_back_button"),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrivacyPointCard(
                    title = "100% Offline & Local",
                    body = "All workspace records, notes, Secret Zone entries, and preferences live exclusively on your device in Room SQLite and Jetpack DataStore. Nova Space makes zero network requests and includes zero analytics or ad SDKs."
                )
                PrivacyPointCard(
                    title = "Scoped App Discovery",
                    body = "Nova Space declares a scoped <queries> filter for standard MAIN/LAUNCHER intents rather than requesting QUERY_ALL_PACKAGES. It never reads or copies private databases, login tokens, cookies, or files belonging to other apps."
                )
                PrivacyPointCard(
                    title = "Secret Zone Cryptography",
                    body = "Your Secret Zone PIN is never saved in plaintext or written to logs. Nova Space derives a 256-bit key with PBKDF2-HMAC-SHA256 (120,000 iterations) and a 16-byte SecureRandom salt, storing only the salt and derived hash."
                )
                PrivacyPointCard(
                    title = "No Hardware Identifier Access",
                    body = "Restricted identifiers such as IMEI, SIM serial numbers, and MAC addresses are never accessed or spoofed. Each Workspace has its own app-generated random UUID stored locally inside Nova Space; regenerating or deleting one Workspace affects only that Workspace."
                )
            }
        }
    }
}

@Composable
private fun PrivacyPointCard(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NovaTheme.colors.subtleBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTheme.colors.secondaryText,
            )
        }
    }
}
