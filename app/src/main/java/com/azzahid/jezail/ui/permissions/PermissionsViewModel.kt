package com.azzahid.jezail.ui.permissions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.azzahid.jezail.core.data.models.PermissionStatus
import com.azzahid.jezail.features.permissions.PermissionManager

class PermissionsViewModel : ViewModel() {
    
    var permissionsState by mutableStateOf<List<PermissionStatus>>(emptyList())
        private set
    
    var showPermissionDialog by mutableStateOf(false)
        private set
        
    var selectedPermission by mutableStateOf<PermissionStatus?>(null)
        private set
    
    fun loadPermissions(context: Context) {
        permissionsState = PermissionManager.getAllPermissionStatuses(context)
    }
    
    fun refreshPermissions(context: Context) {
        loadPermissions(context)
    }
    
    fun showPermissionDetails(permission: PermissionStatus) {
        selectedPermission = permission
        showPermissionDialog = true
    }
    
    fun hidePermissionDialog() {
        showPermissionDialog = false
        selectedPermission = null
    }
    
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return PermissionManager.hasAllRequiredPermissions(context)
    }
    
    fun getMissingRequiredPermissions(context: Context): List<PermissionStatus> {
        return PermissionManager.getMissingPermissions(context)
    }
}