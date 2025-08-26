package com.azzahid.jezail.features.managers

import android.content.Context
import com.azzahid.jezail.core.services.withRootFS
import com.azzahid.jezail.core.services.withRootFSFile
import com.azzahid.jezail.core.utils.downloadFile
import com.azzahid.jezail.core.utils.extractXZFile
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object FridaManager {
    private const val TAG = "FridaManager"
    const val FRIDA_PORT = "27042"
    val rootPath = File("/data/local/tmp/.jezail/frida")
    val versionFilePath = File(rootPath, "version")
    val fridaBinaryPath = File(rootPath, "frida-server")

    private val supportedAbi = listOf("arm64", "arm", "x86", "x86_64")
    private const val FRIDA_API_URL = "https://api.github.com/repos/frida/frida/releases/latest"

    private val abi: String by lazy {
        val abiResult = Shell.cmd("getprop ro.product.cpu.abi").exec()
        supportedAbi.firstOrNull { abiResult.out.joinToString().contains(it) }
            ?: error("Unsupported ABI")
    }

    private fun getDownloadUrl(version: String) =
        "https://github.com/frida/frida/releases/download/$version/frida-server-$version-android-$abi.xz"

    private fun fetchLatestVersion(): String {
        (URL(FRIDA_API_URL).openConnection() as HttpsURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            if (responseCode != 200) throw IOException("Github API failed with $responseCode")
            return inputStream.bufferedReader().use {
                JSONObject(it.readText()).getString("tag_name")
            }
        }
    }

    suspend fun getCurrentVersion(): String? = withRootFSFile(versionFilePath.toString()) {
        if (it.exists()) it.readText() else null
    }

    private suspend fun needsUpdate(): Boolean =
        fetchLatestVersion().let { latest -> getCurrentVersion() == null || getCurrentVersion() != latest }

    private suspend fun downloadAndInstall(context: Context) {
        val latestVersion = fetchLatestVersion()

        val tmpDirPath = File(context.cacheDir, "frida")
        if (tmpDirPath.exists()) tmpDirPath.deleteRecursively()
        tmpDirPath.mkdirs()

        val xz = File(tmpDirPath, "frida-server.xz")
        downloadFile(getDownloadUrl(latestVersion), xz)
        val downloaded = File(tmpDirPath, "frida-server")
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

            rootfs.getFile(versionFilePath.absolutePath).let {
                it.parentFile?.mkdirs()
                it.newOutputStream().use { outputStream ->
                    outputStream.write(latestVersion.toByteArray())
                }
            }
        }


        xz.delete()
        downloaded.delete()
        fridaBinaryPath.setExecutable(true)
    }

    suspend fun install(context: Context) {
        return downloadAndInstall(context)
    }

    suspend fun updateToLatest(context: Context) {
        if (needsUpdate()) downloadAndInstall(context)
    }

    fun start(port: Int? = null) {
        require(fridaBinaryPath.exists())
        val chosenPort = port ?: FRIDA_PORT.toInt()
        fridaBinaryPath.setExecutable(true)
        Shell.cmd(
            "nohup ${fridaBinaryPath.absolutePath} -l 0.0.0.0:$chosenPort >/dev/null 2>&1 &"
        ).exec().takeIf { it.isSuccess } ?: error("Failed to start frida-server")
    }

    fun stop(): Boolean {
        Shell.cmd("pkill -f frida-server", "killall frida-server").exec()
        return !Shell.cmd("pgrep frida-server").exec().isSuccess
    }

    suspend fun getStatus(): Map<String, String> = mapOf(
        "isRunning" to Shell.cmd("pgrep frida-server").exec().isSuccess.toString(),
        "port" to FRIDA_PORT,
        "version" to (getCurrentVersion() ?: "not installed")
    )

    suspend fun getInfo(): Map<String, String> = mapOf(
        "currentVersion" to (getCurrentVersion() ?: "not installed"),
        "latestVersion" to runCatching { fetchLatestVersion() }.getOrDefault("unknown"),
        "needsUpdate" to needsUpdate().toString(),
        "installPath" to fridaBinaryPath.absolutePath
    )
}
