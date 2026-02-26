package com.azzahid.jezail.ui.permissions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.azzahid.jezail.core.data.models.PermissionStatus
import com.azzahid.jezail.features.permissions.PermissionManager

class PermissionsViewModel : ViewModel() {

    var permissionsState by mutableStateOf<List<PermissionStatus>>(emptyList())
        private set

    fun loadPermissions(context: Context) {
        permissionsState = PermissionManager.getAllPermissionStatuses(context)
    }

    fun refreshPermissions(context: Context) {
        loadPermissions(context)
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        return PermissionManager.hasAllRequiredPermissions(context)
    }

    fun getMissingRequiredPermissions(context: Context): List<PermissionStatus> {
        return PermissionManager.getMissingPermissions(context)
    }
}
