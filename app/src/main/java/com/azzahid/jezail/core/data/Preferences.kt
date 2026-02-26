package com.azzahid.jezail.core.data

import android.content.Context
import android.content.SharedPreferences
import com.azzahid.jezail.JezailApp

object Preferences {

    private const val PREFS_NAME = "jezail_prefs"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_ADB_PORT = "adb_port"
    private const val DEFAULT_PORT = 8080
    private const val DEFAULT_ADB_PORT = 5555

    private val prefs: SharedPreferences
        get() = JezailApp.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var adbPort: Int
        get() = prefs.getInt(KEY_ADB_PORT, DEFAULT_ADB_PORT)
        set(value) = prefs.edit().putInt(KEY_ADB_PORT, value).apply()
}
