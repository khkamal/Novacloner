package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.example.domain.model.InstalledAppInfo
import com.example.utils.AppLaunchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class InstalledAppsRepository(private val context: Context) {

    private val iconCache = ConcurrentHashMap<String, Drawable>()

    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }

        val selfPackage = context.packageName
        val seenPackages = HashSet<String>()
        val result = ArrayList<InstalledAppInfo>(resolveList.size)

        for (resolveInfo in resolveList) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == selfPackage || !seenPackages.add(pkg)) continue

            val appInfo = activityInfo.applicationInfo
            val label = runCatching {
                resolveInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
                    ?: appInfo.loadLabel(pm).toString()
            }.getOrDefault(pkg)

            val versionName = runCatching {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                pkgInfo.versionName ?: "1.0"
            }.getOrDefault("—")

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    versionName = versionName,
                    isSystemApp = isSystem,
                )
            )
        }

        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
        result
    }

    suspend fun loadAppIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        iconCache[packageName]?.let { return@withContext it }
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            iconCache[packageName] = drawable
            drawable
        }.getOrNull()
    }

    suspend fun isAppInstalled(packageName: String?): Boolean = withContext(Dispatchers.IO) {
        AppLaunchHelper.isPackageInstalled(context, packageName)
    }
}
