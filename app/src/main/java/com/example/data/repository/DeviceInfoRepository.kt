package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import com.example.data.local.PreferencesManager
import com.example.domain.model.DeviceInfoSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

class DeviceInfoRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
) {

    suspend fun getDeviceInfoSnapshot(): DeviceInfoSnapshot = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val versionName = packageInfo?.versionName ?: "1.0"
        val versionCode = if (packageInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } else {
            1L
        }

        val metrics = context.resources.displayMetrics
        val (availableBytes, totalBytes) = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault(null to null)

        val locale = Locale.getDefault()
        val timeZone = TimeZone.getDefault()
        val instanceId = preferencesManager.getOrCreateAppGeneratedInstanceId()

        DeviceInfoSnapshot(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase(Locale.ROOT) },
            model = Build.MODEL ?: "Unknown",
            deviceCode = Build.DEVICE ?: "Unknown",
            appVersionName = versionName,
            appVersionCode = versionCode,
            packageName = context.packageName,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            localeDisplay = "${locale.displayName} (${locale.toLanguageTag()})",
            timeZoneDisplay = "${timeZone.id} (${timeZone.getDisplayName(false, TimeZone.SHORT, locale)})",
            availableStorageBytes = availableBytes,
            totalStorageBytes = totalBytes,
            appGeneratedWorkspaceId = instanceId,
        )
    }

    suspend fun regenerateAppGeneratedWorkspaceId(): String = withContext(Dispatchers.IO) {
        preferencesManager.regenerateAppGeneratedInstanceId()
    }
}
