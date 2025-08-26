package com.azzahid.jezail.features.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.azzahid.jezail.core.data.models.DangerousPermissions
import com.azzahid.jezail.core.data.models.PermissionStatus

object PermissionManager {
    
    fun getRequiredPermissions(): List<String> {
        return buildList {
            // Add permissions that are dangerous and need to be requested at runtime
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // To add more permissions:
            // 1. Add the permission to AndroidManifest.xml
            // 2. Add its info to DangerousPermissions.PERMISSIONS map
            // 3. Add it here if it requires runtime permission
            // Example:
            // add(Manifest.permission.EXAMPLE_PERMISSION)
        }
    }
    
    fun checkPermissionStatus(context: Context, permission: String): PermissionStatus {
        val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val permissionInfo = DangerousPermissions.PERMISSIONS[permission]
        
        return PermissionStatus(
            permission = permission,
            isGranted = isGranted,
            isDangerous = true,
            displayName = permissionInfo?.displayName ?: permission.substringAfterLast('.'),
            description = permissionInfo?.description ?: "Required for app functionality",
            isRequired = permissionInfo?.isRequired ?: true
        )
    }
    
    fun getAllPermissionStatuses(context: Context): List<PermissionStatus> {
        return getRequiredPermissions().map { permission ->
            checkPermissionStatus(context, permission)
        }
    }
    
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getAllPermissionStatuses(context).all { it.isGranted || !it.isRequired }
    }
    
    fun getMissingPermissions(context: Context): List<PermissionStatus> {
        return getAllPermissionStatuses(context).filter { !it.isGranted && it.isRequired }
    }
}