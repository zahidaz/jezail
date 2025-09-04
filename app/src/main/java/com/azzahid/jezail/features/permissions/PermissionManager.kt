package com.azzahid.jezail.features.permissions

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.ContextCompat.checkSelfPermission
import com.azzahid.jezail.core.data.models.PermissionInfo
import com.azzahid.jezail.core.data.models.PermissionStatus

object PermissionManager {

    private val requiredPermissions = buildMap {
        if (SDK_INT >= TIRAMISU) {
            put(
                POST_NOTIFICATIONS, PermissionInfo(
                    displayName = "Notifications",
                    description = "Required for foreground service notifications and system alerts",
                    isRequired = true
                )
            )

            put(
                READ_PHONE_STATE, PermissionInfo(
                    displayName = "Read Phone State",
                    description = "Required for accessing cellular network status",
                    isRequired = true
                )
            )
        }
    }

    fun checkPermissionStatus(context: Context, permission: String): PermissionStatus {
        require(requiredPermissions.containsKey(permission)) {
            "Unknown permission: $permission"
        }
        val info = requiredPermissions[permission]!!
        return PermissionStatus(
            isDangerous = true,
            permission = permission,
            displayName = info.displayName,
            description = info.description,
            isRequired = info.isRequired,
            isGranted = checkSelfPermission(context, permission) == PERMISSION_GRANTED,
        )
    }

    fun getAllPermissionStatuses(context: Context) =
        requiredPermissions.keys.map { checkPermissionStatus(context, it) }

    fun hasAllRequiredPermissions(context: Context) =
        getAllPermissionStatuses(context).all { it.isGranted || !it.isRequired }

    fun getMissingPermissions(context: Context) =
        getAllPermissionStatuses(context).filter { !it.isGranted && it.isRequired }
}
