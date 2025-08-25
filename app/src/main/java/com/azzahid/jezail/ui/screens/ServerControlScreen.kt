package com.azzahid.jezail.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.azzahid.jezail.ui.components.AdbControls
import com.azzahid.jezail.ui.components.AppHeader
import com.azzahid.jezail.ui.components.RootWarningCard
import com.azzahid.jezail.ui.components.ServerControls
import com.azzahid.jezail.ui.components.ServiceStatusCard
import com.azzahid.jezail.ui.components.StatusOverviewCard
import com.azzahid.jezail.ui.components.WebInterfaceButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerControlScreen(
    isServerRunning: Boolean,
    deviceIpAddress: String,
    serverPort: Int,
    isRooted: Boolean,
    isAdbRunning: Boolean,
    adbVersion: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onStartAdb: () -> Unit,
    onStopAdb: () -> Unit,
    onOpenWebUI: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9FAFB)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Header
            AppHeader()

            // System Status Overview
            StatusOverviewCard(
                isRooted = isRooted,
                isServerRunning = isServerRunning,
                isAdbRunning = isAdbRunning,
                rootIcon = Icons.Default.Lock,
                serverIcon = Icons.Default.Info,
                adbIcon = Icons.Default.Phone
            )

            // HTTP Server Section
            ServiceStatusCard(
                title = "HTTP Server",
                icon = Icons.Default.Info,
                isRunning = isServerRunning,
                connectionInfo = if (isServerRunning) "http://$deviceIpAddress:$serverPort" else null
            )

            // Server Controls
            ServerControls(
                isServerRunning = isServerRunning,
                isRooted = isRooted,
                onStartServer = onStartServer,
                onStopServer = onStopServer
            )

            // ADB Section
            ServiceStatusCard(
                title = "ADB Daemon",
                icon = Icons.Default.Phone,
                isRunning = isAdbRunning,
                connectionInfo = if (isAdbRunning) "$deviceIpAddress:5555" else null
            )

            // ADB Controls
            AdbControls(
                isAdbRunning = isAdbRunning,
                isRooted = isRooted,
                onStartAdb = onStartAdb,
                onStopAdb = onStopAdb
            )

            // Web Interface Launch Button
            WebInterfaceButton(
                isServerRunning = isServerRunning,
                isRooted = isRooted,
                onOpenWebUI = onOpenWebUI
            )

            // Root Warning (if not rooted)
            if (!isRooted) {
                RootWarningCard()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}