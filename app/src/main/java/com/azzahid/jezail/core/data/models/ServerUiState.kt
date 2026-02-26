package com.azzahid.jezail.core.data.models

data class ServerUiState(
    val serverStatus: ServerStatus,
    val isRooted: Boolean,
    val isAdbRunning: Boolean,
    val adbVersion: String,
    val isAuthEnabled: Boolean = false,
    val authPin: String = "",
)
