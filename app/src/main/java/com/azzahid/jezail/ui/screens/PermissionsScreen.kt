package com.azzahid.jezail.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azzahid.jezail.core.data.models.PermissionStatus
import com.azzahid.jezail.ui.permissions.PermissionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refreshPermissions(context)
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadPermissions(context)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "App Permissions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(onClick = onNavigateBack) {
                Text("Back")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionsSummaryCard(
            permissions = viewModel.permissionsState,
            onRefresh = { viewModel.refreshPermissions(context) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Dangerous Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (viewModel.permissionsState.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No dangerous permissions required",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.permissionsState) { permission ->
                    PermissionCard(
                        permission = permission,
                        onRequestPermission = { permissionLauncher.launch(permission.permission) },
                        onShowDetails = { viewModel.showPermissionDetails(permission) }
                    )
                }
            }
        }
    }
    
    if (viewModel.showPermissionDialog && viewModel.selectedPermission != null) {
        PermissionDetailsDialog(
            permission = viewModel.selectedPermission!!,
            onDismiss = { viewModel.hidePermissionDialog() },
            onRequestPermission = { 
                permissionLauncher.launch(viewModel.selectedPermission!!.permission)
                viewModel.hidePermissionDialog()
            }
        )
    }
}

@Composable
private fun PermissionsSummaryCard(
    permissions: List<PermissionStatus>,
    onRefresh: () -> Unit
) {
    val grantedCount = permissions.count { it.isGranted }
    val totalCount = permissions.size
    val requiredMissing = permissions.count { !it.isGranted && it.isRequired }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                requiredMissing > 0 -> MaterialTheme.colorScheme.errorContainer
                grantedCount == totalCount -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permission Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "$grantedCount of $totalCount permissions granted",
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (requiredMissing > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$requiredMissing required permissions missing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionCard(
    permission: PermissionStatus,
    onRequestPermission: () -> Unit,
    onShowDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onShowDetails
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = permission.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (permission.isGranted) Icons.Default.Check else {
                            if (permission.isRequired) Icons.Default.Warning else Icons.Default.Close
                        },
                        contentDescription = if (permission.isGranted) "Granted" else "Not granted",
                        tint = if (permission.isGranted) Color(0xFF4CAF50) else {
                            if (permission.isRequired) Color(0xFFFF9800) else Color(0xFFF44336)
                        }
                    )
                    
                    if (!permission.isGranted) {
                        Button(
                            onClick = onRequestPermission
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDetailsDialog(
    permission: PermissionStatus,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null
            )
        },
        title = {
            Text(permission.displayName)
        },
        text = {
            Column {
                Text(permission.description)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Status: ${if (permission.isGranted) "Granted" else "Not Granted"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Required: ${if (permission.isRequired) "Yes" else "Optional"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Permission: ${permission.permission}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (!permission.isGranted) {
                Button(onClick = onRequestPermission) {
                    Text("Grant Permission")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}