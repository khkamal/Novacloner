package com.example.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.repository.InstalledAppsRepository
import com.example.ui.theme.NovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconView(
    packageName: String?,
    appName: String?,
    installedAppsRepository: InstalledAppsRepository,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isUnavailable: Boolean = false,
) {
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = if (packageName.isNullOrBlank()) {
            null
        } else {
            val drawable: Drawable? = installedAppsRepository.loadAppIcon(packageName)
            drawable?.let {
                withContext(Dispatchers.Default) {
                    runCatching { it.toBitmap().asImageBitmap() }.getOrNull()
                }
            }
        }
    }

    val shape = remember { RoundedCornerShape(12.dp) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                when {
                    iconBitmap != null -> MaterialTheme.colorScheme.surfaceContainerHigh
                    isUnavailable -> NovaTheme.colors.warningContainer
                    else -> accentColor.copy(alpha = 0.14f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isUnavailable) {
                    NovaTheme.colors.warning.copy(alpha = 0.45f)
                } else {
                    NovaTheme.colors.subtleBorder.copy(alpha = 0.7f)
                },
                shape = shape
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            iconBitmap != null -> {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = appName?.let { "$it icon" } ?: "Application icon",
                    modifier = Modifier
                        .size(size - 8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            isUnavailable -> {
                Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = "Application unavailable",
                    tint = NovaTheme.colors.warning,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = "No application linked",
                    tint = accentColor,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}
