package com.azzahid.jezail.core.data.models

import kotlinx.serialization.Serializable


@Serializable
data class SimplePackageInfo(
    val packageName: String,
    val name: String,
    val icon: String?,
    val isRunning: Boolean,
    val canLaunch: Boolean,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean
)
