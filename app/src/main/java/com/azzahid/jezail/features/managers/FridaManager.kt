package com.azzahid.jezail.features.managers

import android.content.Context
import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.core.services.withRootFS
import com.azzahid.jezail.core.utils.downloadFile
import com.azzahid.jezail.core.utils.extractXZFile
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object FridaManager {
    private const val TAG = "FridaManager"
    val rootPath = File("/data/local/tmp/.jezail/frida")
    val fridaBinaryPath: File get() = File(rootPath, Preferences.fridaBinaryName)

    private val supportedAbi = listOf("arm64", "arm", "x86", "x86_64")

    private val abi: String by lazy {
        val abiResult = Shell.cmd("getprop ro.product.cpu.abi").exec()
        supportedAbi.firstOrNull { abiResult.out.joinToString().contains(it) }
            ?: error("Unsupported ABI")
    }

    private fun getDownloadUrl(version: String) =
        "https://github.com/frida/frida/releases/download/$version/frida-server-$version-android-$abi.xz"

    private fun fetchLatestVersion(): String {
        val url = URL("https://api.github.com/repos/frida/frida/releases/latest")
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) {
                throw IllegalStateException("Github API returned: ${connection.responseMessage} code: ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use {
                JSONObject(it.readText()).getString("tag_name")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun getCurrentVersion(): String? {
        return runCatching {
            val result = Shell.cmd("$fridaBinaryPath --version").exec()
            if (result.isSuccess) {
                result.out.firstOrNull()?.trim()
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun downloadAndInstall(context: Context) {
        val latestVersion = fetchLatestVersion()

        val tmpDirPath = File(context.cacheDir, "frida")
        if (tmpDirPath.exists()) tmpDirPath.deleteRecursively()
        tmpDirPath.mkdirs()

        try {
            val xz = File(tmpDirPath, "frida-server.xz")
            downloadFile(getDownloadUrl(latestVersion), xz)
            val downloaded = File(tmpDirPath, "frida-server")
            extractXZFile(xz, downloaded)
            withRootFS { rootfs ->
                val targetFile = rootfs.getFile(fridaBinaryPath.absolutePath)
                targetFile.parentFile?.mkdirs()
                downloaded.inputStream().use { input ->
                    targetFile.newOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
            }
        } finally {
            tmpDirPath.deleteRecursively()
        }
    }

    suspend fun install(context: Context) {
        downloadAndInstall(context)
    }

    suspend fun updateToLatest(context: Context) {
        downloadAndInstall(context)
    }

    fun start(port: Int? = null) {
        require(fridaBinaryPath.exists())
        val chosenPort = port ?: Preferences.fridaPort
        val result = Shell.cmd(
            "chmod +x ${fridaBinaryPath.absolutePath}",
            "nohup ${fridaBinaryPath.absolutePath} -l 0.0.0.0:$chosenPort &"
        ).exec()
        if (!result.isSuccess) {
            val msg = buildString {
                appendLine(result.err.joinToString())
                appendLine(result.out.joinToString())
                appendLine("Failed to start Frida server")
                appendLine("Exit code: ${result.code}")
                appendLine("port: $chosenPort")
                appendLine("binary: ${fridaBinaryPath.absolutePath}")
                appendLine("exists ${fridaBinaryPath.exists()}")
                appendLine("canExecute ${fridaBinaryPath.canExecute()}")
                appendLine("canRead ${fridaBinaryPath.canRead()}")
            }
            error(msg)
        }
    }

    fun stop(): Boolean {
        val name = Preferences.fridaBinaryName
        Shell.cmd("pkill -f $name", "killall $name").exec()
        return !Shell.cmd("pgrep $name").exec().isSuccess
    }

    fun getStatus(): Map<String, Any> {
        val name = Preferences.fridaBinaryName
        return mapOf(
            "isRunning" to Shell.cmd("pgrep $name").exec().isSuccess,
            "port" to Preferences.fridaPort,
            "version" to (getCurrentVersion() ?: "not installed")
        )
    }

    fun getInfo(): Map<String, Any> = buildMap {
        val currentVersion = getCurrentVersion()

        if (currentVersion != null) {
            put("currentVersion", currentVersion)
            put("installPath", fridaBinaryPath.absolutePath)
        } else {
            put("currentVersion", "not installed")
            put("installPath", "not installed")
        }

        runCatching {
            val latest = fetchLatestVersion()
            put("latestVersion", latest)
            put("needsUpdate", currentVersion != null && currentVersion != latest)
        }.getOrElse {
            put("latestVersion", it.message ?: "Failed to fetch latest version")
            put("needsUpdate", false)
        }

        put("port", Preferences.fridaPort)
        put("binaryName", Preferences.fridaBinaryName)
        put("abi", abi)
    }


}
