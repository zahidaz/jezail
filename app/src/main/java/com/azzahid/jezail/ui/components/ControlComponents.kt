package com.azzahid.jezail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ServerControls(
    isServerRunning: Boolean,
    isRooted: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onStartServer,
            enabled = !isServerRunning && isRooted,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                disabledContainerColor = Color(0xFFE5E7EB)
            )
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Start Server",
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Start Server",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

        Button(
            onClick = onStopServer,
            enabled = isServerRunning && isRooted,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                disabledContainerColor = Color(0xFFE5E7EB)
            )
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Stop Server",
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Stop Server",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun AdbControls(
    isAdbRunning: Boolean,
    isRooted: Boolean,
    onStartAdb: () -> Unit,
    onStopAdb: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onStartAdb,
            enabled = !isAdbRunning && isRooted,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF059669),
                disabledContainerColor = Color(0xFFE5E7EB)
            )
        ) {
            Text(
                "Start ADB",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

        Button(
            onClick = onStopAdb,
            enabled = isAdbRunning && isRooted,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                disabledContainerColor = Color(0xFFE5E7EB)
            )
        ) {
            Text(
                "Stop ADB",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun WebInterfaceButton(
    isServerRunning: Boolean,
    isRooted: Boolean,
    onOpenWebUI: () -> Unit
) {
    FilledTonalButton(
        onClick = onOpenWebUI,
        enabled = isServerRunning && isRooted,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFFF9FAFB),
            contentColor = Color(0xFF111827),
            disabledContainerColor = Color(0xFFE5E7EB)
        )
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = "Open Web Interface",
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Launch Web Interface",
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}