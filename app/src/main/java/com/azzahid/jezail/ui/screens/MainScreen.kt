package com.azzahid.jezail.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.ui.permissions.PermissionsViewModel
import com.azzahid.jezail.ui.theme.Monospace

@Composable
fun MainScreen(
    isServerRunning: Boolean,
    deviceIpAddress: String,
    serverPort: Int,
    isRooted: Boolean,
    isAdbRunning: Boolean,
    isAuthEnabled: Boolean,
    authPin: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onStartAdb: () -> Unit,
    onStopAdb: () -> Unit,
    onToggleAuth: (Boolean) -> Unit,
    onRegeneratePin: () -> Unit,
    onPortChange: (Int) -> Unit = {},
    onAdbPortChange: (Int) -> Unit = {},
    adbPort: String = Preferences.adbPort.toString(),
    permissionsViewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionsViewModel.refreshPermissions(context)
    }

    LaunchedEffect(Unit) {
        permissionsViewModel.loadPermissions(context)
    }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = "JEZAIL",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = Color(0xFFE53935)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isServerRunning) "http://$deviceIpAddress:$serverPort"
                    else "Server stopped",
                    fontFamily = Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }

            item { SectionLabel("Services") }

            item {
                ServiceRow(
                    label = "HTTP Server",
                    isRunning = isServerRunning,
                    canControl = isRooted,
                    onStart = onStartServer,
                    onStop = onStopServer,
                    port = serverPort,
                    portEditable = !isServerRunning && isRooted,
                    onPortChange = onPortChange
                )
            }

            item {
                ServiceRow(
                    label = "ADB",
                    isRunning = isAdbRunning,
                    canControl = isRooted,
                    onStart = onStartAdb,
                    onStop = onStopAdb,
                    port = adbPort.toIntOrNull() ?: 5555,
                    portEditable = !isAdbRunning && isRooted,
                    onPortChange = onAdbPortChange
                )
            }

            item { Spacer(Modifier.height(24.dp)) }

            item { SectionLabel("Security") }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Authentication", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isAuthEnabled, onCheckedChange = onToggleAuth)
                }
                Divider()
            }

            item {
                AnimatedVisibility(visible = isAuthEnabled) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Pairing PIN",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = authPin,
                                    fontFamily = Monospace,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 6.sp
                                )
                            }
                            TextButton(onClick = onRegeneratePin) {
                                Text("Regenerate")
                            }
                        }
                        Divider()
                    }
                }
            }

            if (!isRooted) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Root access required to control services",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item { SectionLabel("Permissions") }

            val grantedCount = permissionsViewModel.permissionsState.count { it.isGranted }
            val totalCount = permissionsViewModel.permissionsState.size

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$grantedCount / $totalCount granted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { permissionsViewModel.refreshPermissions(context) }) {
                        Text("Refresh")
                    }
                }
                Divider()
            }

            items(permissionsViewModel.permissionsState.filter { !it.isGranted }) { permission ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(permission.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            permission.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { permissionLauncher.launch(permission.permission) }) {
                        Text("Grant")
                    }
                }
                Divider()
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ServiceRow(
    label: String,
    isRunning: Boolean,
    canControl: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    port: Int = 0,
    portEditable: Boolean = false,
    onPortChange: (Int) -> Unit = {}
) {
    var showPortDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(8.dp)
            ) {}
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Port $port",
                    fontFamily = Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.let {
                        if (portEditable) it.clickable { showPortDialog = true } else it
                    }
                )
            }
        }

        if (canControl) {
            TextButton(onClick = if (isRunning) onStop else onStart) {
                Text(if (isRunning) "Stop" else "Start")
            }
        } else {
            Text(
                "Root required",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divider()

    if (showPortDialog) {
        PortDialog(
            title = "$label port",
            currentPort = port,
            onDismiss = { showPortDialog = false },
            onConfirm = { onPortChange(it); showPortDialog = false }
        )
    }
}

@Composable
private fun PortDialog(
    title: String,
    currentPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = portText,
                onValueChange = {
                    if (it.all { c -> c.isDigit() } || it.isEmpty()) {
                        portText = it
                        isError = false
                    }
                },
                label = { Text("Port (1024-65535)") },
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(onClick = {
                val p = portText.toIntOrNull()
                if (p != null && p in 1024..65535) onConfirm(p) else isError = true
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
