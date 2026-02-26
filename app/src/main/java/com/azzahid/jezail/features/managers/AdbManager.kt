package com.azzahid.jezail.features.managers

import com.azzahid.jezail.core.data.Preferences
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import java.io.IOException

object AdbManager {
    private const val ADB_KEYS_DIR = "/data/misc/adb"
    private const val ADB_KEYS_FILE = "$ADB_KEYS_DIR/adb_keys"

    fun getStatus(): Map<String, Any?> {
        val running = Shell.cmd("pgrep adbd").exec().isSuccess
        val activePort = Shell.cmd("getprop service.adb.tcp.port").exec()
            .out.firstOrNull()?.toIntOrNull()
        return mapOf(
            "isRunning" to running,
            "activePort" to activePort,
            "preferredPort" to Preferences.adbPort,
        )
    }

    fun getPort(): Map<String, Int> = mapOf("port" to Preferences.adbPort)

    fun setPort(port: Int) {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        Preferences.adbPort = port
    }

    suspend fun start(port: Int? = null) {
        val actualPort = port ?: Preferences.adbPort

        Shell.cmd(
            "setprop service.adb.tcp.port $actualPort",
            "setprop ctl.restart adbd"
        ).exec()

        delay(1_000)

        val running = Shell.cmd("pgrep adbd").exec().isSuccess
        if (!running) {
            throw IOException("Failed to start ADB daemon")
        }
    }

    fun stop() {
        Shell.cmd(
            "setprop ctl.stop adbd",
            "killall adbd"
        ).exec()

        val stillRunning = Shell.cmd("pgrep adbd").exec().isSuccess
        if (stillRunning) {
            throw IOException("Failed to stop ADB daemon")
        }
    }

    fun installKey(publicKey: String) {
        require(publicKey.isNotBlank()) {
            "Public key cannot be blank"
        }

        val sanitized = publicKey.replace("'", "'\\''")
        val result = Shell.cmd(
            "mkdir -p $ADB_KEYS_DIR",
            "chmod 755 $ADB_KEYS_DIR",
            "echo '${sanitized}' >> $ADB_KEYS_FILE",
            "chmod 644 $ADB_KEYS_FILE",
            "chown system:shell $ADB_KEYS_FILE"
        ).exec()

        if (!result.isSuccess) {
            throw IOException("Failed to install ADB key: ${result.err.joinToString("\n")}")
        }
    }
}
