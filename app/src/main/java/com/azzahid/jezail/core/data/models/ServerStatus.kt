package com.azzahid.jezail.core.data.models

data class ServerStatus(
    val isRunning: Boolean, val port: Int, val ipAddress: String, val url: String?
)