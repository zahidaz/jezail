package com.azzahid.jezail.features

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.azzahid.jezail.core.utils.DrawableEncoder
import kotlinx.serialization.Serializable

fun PackageInfo.toSimplePackageInfo(
    pm: PackageManager,
    context: Context,
    drawableEncoder: DrawableEncoder
): SimplePackageInfo? {
    val app = applicationInfo ?: return null
    return SimplePackageInfo(
        packageName = packageName,
        name = app.nonLocalizedLabel?.toString() ?: pm.getApplicationLabel(app).toString(),
        icon = drawableEncoder.encodeDrawableToBase64(pm.getApplicationIcon(app)),
        isRunning = context.isPackageRunning(packageName),
        canLaunch = pm.getLaunchIntentForPackage(packageName) != null,
        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    )
}

fun Context.isPackageRunning(packageName: String): Boolean {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.runningAppProcesses?.any { it.processName == packageName } ?: false
}

@Serializable
data class SimplePackageInfo(
    val packageName: String,
    val name: String,
    val icon: String?,
    val isRunning: Boolean,
    val canLaunch: Boolean,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean
)
