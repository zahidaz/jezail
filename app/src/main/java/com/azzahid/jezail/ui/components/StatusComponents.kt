package com.azzahid.jezail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatusOverviewCard(
    isRooted: Boolean,
    isServerRunning: Boolean,
    isAdbRunning: Boolean,
    rootIcon: ImageVector,
    serverIcon: ImageVector,
    adbIcon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusItem(
                    icon = rootIcon,
                    label = "Root",
                    status = if (isRooted) "Available" else "Required",
                    isOnline = isRooted,
                    modifier = Modifier.weight(1f)
                )
                StatusItem(
                    icon = serverIcon,
                    label = "Server",
                    status = if (isServerRunning) "Online" else "Offline",
                    isOnline = isServerRunning,
                    modifier = Modifier.weight(1f)
                )
                StatusItem(
                    icon = adbIcon,
                    label = "ADB",
                    status = if (isAdbRunning) "Online" else "Offline",
                    isOnline = isAdbRunning,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatusItem(
    icon: ImageVector,
    label: String,
    status: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (isOnline) Color(0xFF059669) else Color(0xFF6B7280)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (isOnline) Color(0xFF059669) else Color(0xFFDC2626),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusDot(isOnline: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = if (isOnline) Color(0xFF059669) else Color(0xFFDC2626),
                shape = CircleShape
            )
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun ServiceStatusCard(
    title: String,
    icon: ImageVector,
    isRunning: Boolean,
    connectionInfo: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF2563EB)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.weight(1f))
                StatusDot(isOnline = isRunning)
            }

            if (isRunning && connectionInfo != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = connectionInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFF9FAFB),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(12.dp)
                )
            }
        }
    }
}