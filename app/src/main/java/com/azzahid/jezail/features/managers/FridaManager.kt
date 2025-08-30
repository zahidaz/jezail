package com.azzahid.jezail.features.managers

import android.content.Context
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
    const val FRIDA_PORT = "27042"
    private const val FRIDA_BINARY_NAME = "frida-server"
    val rootPath = File("/data/local/tmp/.jezail/frida")
    val fridaBinaryPath = File(rootPath, FRIDA_BINARY_NAME)

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
        (url.openConnection() as HttpsURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            if (responseCode != 200) {
                throw IllegalStateException("Github API returned: $responseMessage code: $responseCode")
            }
            return inputStream.bufferedReader().use {
                JSONObject(it.readText()).getString("tag_name")
            }
        }
    }

    fun getCurrentVersion(): String? {
        return runCatching {
            Shell.cmd("$fridaBinaryPath --version").exec().out.joinToString { it.trim() }
        }.getOrNull()
    }

    private suspend fun downloadAndInstall(context: Context) {
        val latestVersion = fetchLatestVersion()

        val tmpDirPath = File(context.cacheDir, "frida")
        if (tmpDirPath.exists()) tmpDirPath.deleteRecursively()
        tmpDirPath.mkdirs()

        val xz = File(tmpDirPath, "${FRIDA_BINARY_NAME}.xz")
        downloadFile(getDownloadUrl(latestVersion), xz)
        val downloaded = File(tmpDirPath, FRIDA_BINARY_NAME)
        extractXZFile(xz, downloaded)
        withRootFS { rootfs ->
            val sourceFile = downloaded
            val targetFile = rootfs.getFile(fridaBinaryPath.absolutePath)
            targetFile.parentFile?.mkdirs()
            sourceFile.inputStream().use { input ->
                targetFile.newOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }


        xz.delete()
        downloaded.delete()
    }

    suspend fun install(context: Context) {
        downloadAndInstall(context)
    }

    suspend fun updateToLatest(context: Context) {
        downloadAndInstall(context)
    }

    fun start(port: Int? = null) {
        require(fridaBinaryPath.exists())
        val chosenPort = port ?: FRIDA_PORT.toInt()
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
        Shell.cmd("pkill -f $FRIDA_BINARY_NAME", "killall $FRIDA_BINARY_NAME").exec()
        return !Shell.cmd("pgrep $FRIDA_BINARY_NAME").exec().isSuccess
    }

    fun getStatus(): Map<String, Any> = mapOf(
        "isRunning" to Shell.cmd("pgrep $FRIDA_BINARY_NAME").exec().isSuccess,
        "port" to FRIDA_PORT,
        "version" to (getCurrentVersion() ?: "not installed")
    )

    fun getInfo(): Map<String, Any> = buildMap {
        val current = getCurrentVersion()
        put("currentVersion", current ?: "not installed")
        runCatching {
            val latest = fetchLatestVersion()
            put("latestVersion", latest)
            put("needsUpdate", current != null && current != latest)
        }.getOrElse {
            put("latestVersion", it.message ?: "Failed to fetch latest version")
            put("needsUpdate", false)
        }
        put("installPath", current?.let { fridaBinaryPath.absolutePath } ?: "not installed")
    }

}
