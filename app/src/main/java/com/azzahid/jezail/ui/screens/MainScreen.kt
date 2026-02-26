package com.azzahid.jezail.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.core.data.models.PermissionStatus
import com.azzahid.jezail.ui.permissions.PermissionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServerRunning: Boolean,
    deviceIpAddress: String,
    serverPort: Int,
    isRooted: Boolean,
    isAdbRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onStartAdb: () -> Unit,
    onStopAdb: () -> Unit,
    onPortChange: (Int) -> Unit = {},
    adbPort: String = Preferences.adbPort.toString(),
    permissionsViewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionsViewModel.refreshPermissions(context)
    }

    LaunchedEffect(Unit) {
        permissionsViewModel.loadPermissions(context)
    }
    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "JEZAIL",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                StatusOverviewCard(
                    isRooted = isRooted,
                    isServerRunning = isServerRunning,
                    isAdbRunning = isAdbRunning
                )
            }

            item {
                CompactServiceCard(
                    title = "HTTP Server",
                    isRunning = isServerRunning,
                    connectionInfo = if (isServerRunning) "http://$deviceIpAddress:$serverPort" else null,
                    onStart = if (isRooted) onStartServer else null,
                    onStop = if (isRooted) onStopServer else null,
                    port = serverPort,
                    onPortChange = onPortChange,
                    allowPortChange = !isServerRunning && isRooted
                )
            }

            item {
                CompactServiceCard(
                    title = "ADB Daemon",
                    isRunning = isAdbRunning,
                    connectionInfo = if (isAdbRunning) "$deviceIpAddress:$adbPort" else null,
                    onStart = if (isRooted) onStartAdb else null,
                    onStop = if (isRooted) onStopAdb else null
                )
            }

            item {
                AnimatedVisibility(
                    visible = !isRooted,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CompactWarningCard()
                }
            }

            item {
                Text(
                    text = "App Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                PermissionsSummaryCard(
                    permissions = permissionsViewModel.permissionsState,
                    onRefresh = { permissionsViewModel.refreshPermissions(context) }
                )
            }

            if (permissionsViewModel.permissionsState.all { it.isGranted }) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "All permissions granted",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                items(permissionsViewModel.permissionsState) { permission ->
                    CompactPermissionCard(
                        permission = permission,
                        onRequestPermission = { permissionLauncher.launch(permission.permission) }
                    )
                }
            }
        }
    }
}

@Composable
fun PortChangeDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Server Port",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose a port number between 1024 and 65535 for the HTTP server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { newValue ->
                        // Only allow numeric input
                        if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                            portText = newValue
                            isError = false
                        }
                    },
                    label = { Text("Port Number") },
                    placeholder = { Text("e.g., 8080") },
                    isError = isError,
                    supportingText = if (isError) {
                        {
                            Text(
                                text = "Port must be between 1024 and 65535",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        {
                            Text(
                                text = "Current: $currentPort",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull()
                    if (port != null && port in 1024..65535) {
                        onConfirm(port)
                    } else {
                        isError = true
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Update Port",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
fun StatusOverviewCard(
    isRooted: Boolean,
    isServerRunning: Boolean,
    isAdbRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusIndicator(
                    label = "Root Access",
                    isActive = isRooted,
                    modifier = Modifier.weight(1f)
                )
                StatusIndicator(
                    label = "HTTP Server",
                    isActive = isServerRunning,
                    modifier = Modifier.weight(1f)
                )
                StatusIndicator(
                    label = "ADB Daemon",
                    isActive = isAdbRunning,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val statusText = if (isActive) "Active" else "Inactive"

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CompactServiceCard(
    title: String,
    isRunning: Boolean,
    connectionInfo: String?,
    onStart: (() -> Unit)?,
    onStop: (() -> Unit)?,
    port: Int? = null,
    onPortChange: ((Int) -> Unit)? = null,
    allowPortChange: Boolean = false
) {
    var showPortDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isRunning) "Running" else "Stopped",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isRunning && connectionInfo != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = connectionInfo,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (!isRunning && port != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Server Configuration",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Port: $port",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (allowPortChange) {
                            FilledTonalButton(
                                onClick = { showPortDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Configure",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Text(
                                text = "Fixed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onStart != null && !isRunning) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Start",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (onStop != null && isRunning) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Stop",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (onStart == null && onStop == null) {
                    Text(
                        text = "Root access required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    if (showPortDialog && onPortChange != null && port != null) {
        PortChangeDialog(
            currentPort = port,
            onDismiss = { showPortDialog = false },
            onConfirm = { newPort ->
                onPortChange(newPort)
                showPortDialog = false
            }
        )
    }
}

@Composable
fun CompactWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Limited Functionality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Root access is required to start and stop services",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun PermissionsSummaryCard(
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
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text("Refresh", fontSize = 12.sp)
                }
            }

            Text(
                text = "$grantedCount of $totalCount permissions granted",
                style = MaterialTheme.typography.bodySmall
            )

            if (requiredMissing > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$requiredMissing required permissions missing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun CompactPermissionCard(
    permission: PermissionStatus,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = permission.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
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
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            permission.isGranted -> MaterialTheme.colorScheme.primaryContainer
                            permission.isRequired -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when {
                                permission.isGranted -> "Granted"
                                permission.isRequired -> "Required"
                                else -> "Denied"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                permission.isGranted -> MaterialTheme.colorScheme.onPrimaryContainer
                                permission.isRequired -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (!permission.isGranted) {
                        Button(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Grant Access",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

