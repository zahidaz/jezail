package com.azzahid.jezail.features.managers

import com.azzahid.jezail.core.data.Preferences
import java.util.UUID
import kotlin.random.Random

object AuthManager {

    fun generatePin(): String {
        val pin = Random.nextInt(100000, 999999).toString()
        Preferences.authPin = pin
        return pin
    }

    fun generateToken(): String {
        val token = UUID.randomUUID().toString()
        Preferences.authToken = token
        return token
    }

    fun pair(pin: String): String? {
        if (pin != Preferences.authPin) return null
        return generateToken()
    }

    fun validateToken(token: String): Boolean = token.isNotEmpty() && token == Preferences.authToken

    fun isEnabled(): Boolean = Preferences.authEnabled

    fun setEnabled(enabled: Boolean) {
        Preferences.authEnabled = enabled
        if (enabled) {
            generatePin()
            generateToken()
        }
    }

    fun regeneratePin() {
        generatePin()
        Preferences.authToken = ""
    }
}
