package com.azzahid.jezail.core.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PermissionStatus(
    val permission: String,
    val isGranted: Boolean,
    val isDangerous: Boolean,
    val displayName: String,
    val description: String,
    val isRequired: Boolean = true
)


data class PermissionInfo(
    val displayName: String,
    val description: String,
    val isRequired: Boolean = true
)