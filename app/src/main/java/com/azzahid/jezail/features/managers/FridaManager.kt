package com.azzahid.jezail.features.managers

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object FridaManager {
    private const val TAG = "FridaManager"
    const val FRIDA_PORT = "27042"
    private val rootFileSystem = FileSystemManager.getLocal()
    private val fridaInstallPath = rootFileSystem.getFile("/data/local/tmp/frida")
    private val fridaBinary = rootFileSystem.getFile(fridaInstallPath, "frida-server")
    private val versionFile = rootFileSystem.getFile(fridaInstallPath, ".version")
    private val supportedAbi = listOf("arm64", "arm", "x86", "x86_64")
    private const val githubApiUrl = "https://api.github.com/repos/frida/frida/releases/latest"

    private val abi: String = run {
        val abiResult = Shell.cmd("getprop ro.product.cpu.abi").exec()
        supportedAbi.firstOrNull { abiResult.out.joinToString().contains(it) }
            ?: throw RuntimeException("UnsupportedAbi")
    }

    private fun getDownloadUrlForVersion(version: String): String =
        "https://github.com/frida/frida/releases/download/$version/frida-server-$version-android-$abi.xz"

    private fun fetchLatestVersion(): String {
        val connection = URL(githubApiUrl).openConnection() as HttpsURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
        }
        if (connection.responseCode != 200) {
            throw IOException("Failed to fetch latest version: ${connection.responseCode}")
        }
        return connection.inputStream.bufferedReader().use {
            JSONObject(it.readText()).getString("tag_name")
        }
    }

    private fun getCurrentVersion(): String? {
        return try {
            if (versionFile.exists()) {
                versionFile.newInputStream().bufferedReader().use { it.readText().trim() }
            } else null
        } catch (e: Exception) {
            throw IOException("Failed to read version file", e)
        }
    }

    private fun saveCurrentVersion(version: String) {
        try {
            fridaInstallPath.mkdirs()
            versionFile.newOutputStream().bufferedWriter().use {
                it.write(version)
                it.flush()
            }
        } catch (e: Exception) {
            throw IOException("Failed to save version", e)
        }
    }

    private fun needsUpdate(): Boolean {
        val currentVersion = getCurrentVersion()
        val latestVersion = fetchLatestVersion()
        return currentVersion == null || currentVersion != latestVersion
    }

    private suspend fun downloadFile(url: String, context: Context): File =
        withContext(Dispatchers.IO) {
            val downloadDir = File(context.getExternalFilesDir(null), "downloads")
            val destination = File(downloadDir, "frida-server.xz")
            destination.parentFile?.mkdirs()

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 60000
            }
            if (connection.responseCode != 200) {
                throw IOException("Download failed: ${connection.responseCode}")
            }

            destination.delete()
            connection.inputStream.use { input ->
                FileOutputStream(destination, false).use { output ->
                    input.copyTo(output)
                }
            }

            if (!destination.exists() || destination.length() == 0L) {
                throw IOException("Downloaded file is empty")
            }
            destination
        }

    private fun downloadAndInstallLatest(context: Context): String {
        val latestVersion = fetchLatestVersion()
        fridaInstallPath.mkdirs()

        val downloadedFile = runBlocking {
            downloadFile(getDownloadUrlForVersion(latestVersion), context)
        }

        val extractedBinary = File(downloadedFile.parentFile, "frida-server")
        extractXZFile(downloadedFile, extractedBinary)

        Shell.cmd(
            "mv ${extractedBinary.absolutePath} ${fridaBinary.absolutePath}",
            "chmod 755 ${fridaBinary.absolutePath}",
            "chown shell:shell ${fridaBinary.absolutePath}"
        ).exec()

        saveCurrentVersion(latestVersion)
        return "Frida server $latestVersion installed successfully"
    }

    fun getStatus(): Map<String, String> {
        val running = Shell.cmd("pgrep frida-server").exec().isSuccess
        return mapOf(
            "isRunning" to running.toString(),
            "port" to FRIDA_PORT,
            "version" to (getCurrentVersion() ?: "not installed")
        )
    }

    fun getInfo(): Map<String, String> {
        return mapOf(
            "currentVersion" to (getCurrentVersion() ?: "not installed"),
            "latestVersion" to runCatching { fetchLatestVersion() }.getOrDefault("unknown"),
            "needsUpdate" to needsUpdate().toString(),
            "installPath" to fridaInstallPath.absolutePath
        )
    }

    fun start(port: Int? = null) {
        if (!fridaBinary.exists() || !fridaBinary.isFile) {
            throw IllegalStateException("Frida server not found. Please install first using install()")
        }

        val actualPort = port ?: FRIDA_PORT.toInt()
        Shell.cmd("chmod 755 ${fridaBinary.absolutePath}").exec()
        val result = Shell.cmd(
            "nohup ${fridaBinary.absolutePath} -l 0.0.0.0:$actualPort >/dev/null 2>&1 &"
        ).exec()

        if (!result.isSuccess) {
            throw IOException("Failed to start Frida server")
        }
    }

    fun stop(): Boolean {
        val stop = Shell.cmd(
            "pkill -f frida-server",
            "killall frida-server"
        ).exec()

        val stopped = Shell.cmd("pgrep frida-server").exec()
        return stopped.isSuccess

    }


    fun install(context: Context) {
        downloadAndInstallLatest(context)
    }

    fun updateToLatest(context: Context) {
        if (needsUpdate()) downloadAndInstallLatest(context)
    }

    private fun extractXZFile(xzFile: File, destinationFile: File) {
        if (!xzFile.exists()) {
            throw IOException("$xzFile does not exist")
        }
        if (destinationFile.exists()) {
            throw IOException("$destinationFile already exists")
        }

        Log.d(TAG, "Extracting ${xzFile.absolutePath} to ${destinationFile.absolutePath}")
        destinationFile.parentFile?.mkdirs()

        try {
            BufferedInputStream(FileInputStream(xzFile)).use { input ->
                XZInputStream(input).use { xzIn ->
                    FileOutputStream(destinationFile, false).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (xzIn.read(buffer).also { bytesRead = it } > 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            Log.d(TAG, "Extraction completed successfully")
        } catch (e: IOException) {
            throw IOException("Failed to extract XZ file", e)
        }
    }
}
