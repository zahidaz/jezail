package com.azzahid.jezail.core.data

import android.content.Context
import android.content.SharedPreferences
import com.azzahid.jezail.JezailApp

object Preferences {

    private const val PREFS_NAME = "jezail_prefs"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_ADB_PORT = "adb_port"
    private const val KEY_AUTH_ENABLED = "auth_enabled"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_AUTH_PIN = "auth_pin"
    private const val KEY_FRIDA_PORT = "frida_port"
    private const val KEY_FRIDA_BINARY_NAME = "frida_binary_name"
    private const val DEFAULT_PORT = 8080
    private const val DEFAULT_ADB_PORT = 5555
    private const val DEFAULT_FRIDA_PORT = 27042
    private const val DEFAULT_FRIDA_BINARY_NAME = "frida-server"

    private val prefs: SharedPreferences
        get() = JezailApp.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var adbPort: Int
        get() = prefs.getInt(KEY_ADB_PORT, DEFAULT_ADB_PORT)
        set(value) = prefs.edit().putInt(KEY_ADB_PORT, value).apply()

    var authEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTH_ENABLED, value).apply()

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var authPin: String
        get() = prefs.getString(KEY_AUTH_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_PIN, value).apply()

    var fridaPort: Int
        get() = prefs.getInt(KEY_FRIDA_PORT, DEFAULT_FRIDA_PORT)
        set(value) = prefs.edit().putInt(KEY_FRIDA_PORT, value).apply()

    var fridaBinaryName: String
        get() = prefs.getString(KEY_FRIDA_BINARY_NAME, DEFAULT_FRIDA_BINARY_NAME) ?: DEFAULT_FRIDA_BINARY_NAME
        set(value) = prefs.edit().putString(KEY_FRIDA_BINARY_NAME, value).apply()
}
