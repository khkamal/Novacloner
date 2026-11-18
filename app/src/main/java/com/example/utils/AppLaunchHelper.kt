package com.example.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

sealed interface LaunchResult {
    data object Success : LaunchResult
    data object NoAppLinked : LaunchResult
    data class NotInstalled(val packageName: String) : LaunchResult
    data class NoLaunchIntent(val packageName: String) : LaunchResult
    data class SecurityRestricted(val packageName: String) : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

object AppLaunchHelper {

    fun isPackageInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun launchApp(context: Context, packageName: String?): LaunchResult {
        if (packageName.isNullOrBlank()) {
            return LaunchResult.NoAppLinked
        }
        val pm = context.packageManager
        if (!isPackageInstalled(context, packageName)) {
            return LaunchResult.NotInstalled(packageName)
        }
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NoLaunchIntent(packageName)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(launchIntent)
            LaunchResult.Success
        } catch (_: SecurityException) {
            LaunchResult.SecurityRestricted(packageName)
        } catch (_: ActivityNotFoundException) {
            LaunchResult.NoLaunchIntent(packageName)
        } catch (e: Exception) {
            LaunchResult.Failed(e.localizedMessage ?: "Unable to start application.")
        }
    }

    fun messageForFailure(result: LaunchResult): String {
        return when (result) {
            LaunchResult.Success -> ""
            LaunchResult.NoAppLinked -> "No installed application is linked to this workspace yet."
            is LaunchResult.NotInstalled ->
                "\"${result.packageName}\" is no longer installed on this device. You can reassign or clear the association."
            is LaunchResult.NoLaunchIntent ->
                "\"${result.packageName}\" does not expose a user-launchable activity or is currently disabled."
            is LaunchResult.SecurityRestricted ->
                "Android denied launching \"${result.packageName}\" due to missing or restricted system permissions."
            is LaunchResult.Failed -> result.reason
        }
    }
}
