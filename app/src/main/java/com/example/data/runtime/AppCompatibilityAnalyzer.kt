package com.example.data.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.domain.model.AppCompatibilityReport
import com.example.domain.model.CompatibilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Inspects observable public PackageManager metadata to surface truthful compatibility notes.
 * Does not use hidden APIs, signature bypasses, Play Integrity bypasses, or identifier spoofing.
 */
class AppCompatibilityAnalyzer(private val context: Context) {

    suspend fun analyze(packageName: String?): AppCompatibilityReport? = withContext(Dispatchers.IO) {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        val pm = context.packageManager

        val packageInfo = queryPackageInfo(pm, pkg)
        if (packageInfo == null) {
            return@withContext AppCompatibilityReport(
                packageName = pkg,
                status = CompatibilityStatus.UNAVAILABLE,
                isInstalled = false,
                hasLaunchableActivity = false,
                warnings = listOf("Package \"$pkg\" is not installed or is not visible to Nova Space."),
            )
        }

        val appInfo = packageInfo.applicationInfo
        val launchIntent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
        val hasLaunchIntent = launchIntent != null

        val isSystemApp = appInfo != null &&
            ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)

        val isSplitApk = appInfo != null && !appInfo.splitSourceDirs.isNullOrEmpty()

        val hasNativeLibs = appInfo?.nativeLibraryDir
            ?.let { path ->
                runCatching {
                    val dir = File(path)
                    dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
                }.getOrDefault(false)
            }
            ?: false

        @Suppress("DEPRECATION")
        val sharedUid = packageInfo.sharedUserId?.takeIf { it.isNotBlank() }

        val targetSdk = appInfo?.targetSdkVersion ?: 0
        val minSdk = appInfo?.minSdkVersion ?: 0

        val referencesGms = appInfo?.metaData
            ?.let { meta ->
                meta.containsKey("com.google.android.gms.version") ||
                    meta.keySet().any { it.startsWith("com.google.firebase.") }
            }
            ?: false

        val warnings = buildList {
            if (!hasLaunchIntent) {
                add("No user-launchable MAIN/LAUNCHER activity exposed; cannot be launched directly.")
            }
            if (isSystemApp) {
                add("System-installed application; OS privilege and signature boundaries prevent cloning.")
            }
            if (isSplitApk) {
                add("Uses split APKs (App Bundle); launches cleanly on host, but container engines cannot load single base APKs safely.")
            }
            if (hasNativeLibs) {
                add("Contains native (.so) libraries; isolated containers may conflict with ABI or path expectations.")
            }
            if (sharedUid != null) {
                add("Declares sharedUserId (\"$sharedUid\"); coupled to vendor package UID group.")
            }
            if (referencesGms) {
                add("References Google Play Services / Firebase metadata; separate containers without native GMS often fail sign-in or Play Integrity.")
            }
        }

        val status = when {
            !hasLaunchIntent -> CompatibilityStatus.UNAVAILABLE
            isSystemApp || sharedUid != null -> CompatibilityStatus.LIMITED
            else -> CompatibilityStatus.HOST_LAUNCHABLE
        }

        AppCompatibilityReport(
            packageName = pkg,
            status = status,
            isInstalled = true,
            hasLaunchableActivity = hasLaunchIntent,
            isSystemApp = isSystemApp,
            isSplitApk = isSplitApk,
            hasNativeLibraries = hasNativeLibs,
            sharedUserId = sharedUid,
            targetSdkVersion = targetSdk,
            minSdkVersion = minSdk,
            referencesPlayServices = referencesGms,
            warnings = warnings,
        )
    }

    private fun queryPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return try {
            val flags = PackageManager.GET_META_DATA.toLong()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags.toInt())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
