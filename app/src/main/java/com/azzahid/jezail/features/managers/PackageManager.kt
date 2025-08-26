package com.azzahid.jezail.features.managers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.utils.DrawableEncoder
import com.azzahid.jezail.features.SimplePackageInfo
import com.azzahid.jezail.features.toSimplePackageInfo
import com.google.gson.Gson
import com.topjohnwu.superuser.Shell
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object PackageManager {

    private const val TAG = "AppManager"
    private val drawableEncoder = DrawableEncoder()
    private val gson = Gson()

    fun getInstalledApps(
        context: Context = JezailApp.Companion.appContext,
        includeSystem: Boolean = true,
    ): List<SimplePackageInfo> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { it }
            .filter { pkgInfo ->
                val isSystemApp =
                    (pkgInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) != 0
                if (includeSystem) true else !isSystemApp
            }.mapNotNull {
                runCatching {
                    it.toSimplePackageInfo(pm, context, drawableEncoder)
                }.getOrNull()
            }
    }

    fun getUserInstalledApps(context: Context = JezailApp.Companion.appContext) =
        getInstalledApps(context, includeSystem = false)

    fun getSystemInstalledApps(context: Context = JezailApp.Companion.appContext) =
        getInstalledApps(context, includeSystem = true)

    fun getAllInstalledApps(context: Context = JezailApp.Companion.appContext): List<SimplePackageInfo> =
        getInstalledApps(context, includeSystem = false) + getInstalledApps(
            context, includeSystem = true
        )

    fun getAppDetails(
        packageName: String, context: Context = JezailApp.Companion.appContext
    ): JsonElement {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        val pm = context.packageManager
        val packageFlags =
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA

        val packageInfo = runCatching {
            pm.getPackageInfo(packageName, packageFlags)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        return runCatching {
            Json.Default.parseToJsonElement(gson.toJson(packageInfo))
        }.getOrElse {
            error("Failed to parse package info for '$packageName' to JSON")
        }
    }

    fun getAppSimpleDetails(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): SimplePackageInfo? {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        val pm = context.packageManager
        val packageInfo = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        return packageInfo.toSimplePackageInfo(pm, context, drawableEncoder)
    }

    fun tryLaunchApp(
        packageName: String,
        activityName: String? = null,
        context: Context = JezailApp.Companion.appContext
    ) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(activityName?.isNotBlank() != false) { "Activity name cannot be blank" }

        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val shellCommand = if (activityName != null) {
            "am start -n $packageName/$activityName"
        } else {
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        }

        val result = Shell.cmd(shellCommand).exec()

        require(result.isSuccess) {
            "Failed to launch app '$packageName'${activityName?.let { " with activity '$it'" } ?: ""}: ${
                result.err.joinToString("\n").ifEmpty { "Unknown error" }
            }"
        }
    }


    fun tryStopApp(packageName: String, context: Context = JezailApp.Companion.appContext) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        val pm = context.packageManager
        runCatching {
            pm.getPackageInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val result = Shell.cmd("am force-stop $packageName").exec()
        require(result.isSuccess) {
            "Failed to stop app '$packageName': ${
                result.err.joinToString("\n").ifEmpty { "Unknown error" }
            }"
        }
    }

    fun tryUninstallApp(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(packageName != context.packageName) { "Cannot uninstall self '${context.packageName}'" }

        val appInfo = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        require(!isSystemApp) { "Cannot uninstall system app '$packageName'" }

        val result = Shell.cmd("pm uninstall $packageName").exec()

        require(result.isSuccess && result.out.any { it.contains("Success", ignoreCase = true) }) {
            "Failed to uninstall '$packageName': ${
                (result.err + result.out).joinToString("\n").ifEmpty { "Unknown error" }
            }"
        }
    }

    fun tryInstallApp(
        apk: File,
        forceInstall: Boolean = false,
        grantPermissions: Boolean = false,
    ) {
        require(apk.exists()) { "APK file does not exist: '${apk.absolutePath}'" }
        require(apk.canRead()) { "APK file is not readable: '${apk.absolutePath}'" }

        val flags = buildString {
            if (forceInstall) append(" -r")
            if (grantPermissions) append(" -g")
        }

        val result = Shell.cmd("pm install $flags '${apk.absolutePath}'").exec()

        require(result.isSuccess && result.out.any { it.contains("Success", ignoreCase = true) }) {
            "Failed to install '${apk.absolutePath}': ${
                (result.err + result.out).joinToString("\n").ifEmpty { "Unknown error" }
            }"
        }
    }

    fun grantPermission(
        packageName: String,
        permission: String,
        context: Context = JezailApp.Companion.appContext
    ) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(permission.isNotBlank()) { "Permission cannot be blank" }

        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        require(permission in requestedPermissions) {
            "Permission '$permission' is not declared in manifest for package '$packageName'}"
        }

        val result = Shell.cmd("pm grant $packageName $permission").exec()
        require(result.isSuccess) {
            "Failed to grant permission '$permission' to '$packageName': ${
                result.err.joinToString("\n").ifEmpty { "Unknown error" }
            }"
        }
    }

    fun revokePermission(
        packageName: String,
        permission: String,
        context: Context = JezailApp.Companion.appContext
    ) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(permission.isNotBlank()) { "Permission cannot be blank" }

        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        require(permission in requestedPermissions) {
            "Permission '$permission' is not declared in manifest for package '$packageName'}"
        }

        val result = Shell.cmd("pm revoke $packageName $permission").exec()
        require(result.isSuccess) {
            "Failed to revoke permission '$permission' from '$packageName': ${
                result.err.joinToString(
                    "\n"
                ).ifEmpty { "Unknown error" }
            }"
        }
    }

    fun getGrantedPermissions(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): List<String> {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        val packageInfo: PackageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrElse {
            error("Package '$packageName' not found")
        }
        val permissions = packageInfo.requestedPermissions ?: return emptyList()
        val flags = packageInfo.requestedPermissionsFlags ?: return emptyList()
        return permissions.filterIndexed { index, _ ->
            flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }
    }

    fun getAllPermissions(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): List<String> {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        return packageInfo.requestedPermissions?.toList() ?: emptyList()
    }

    fun isAppRunning(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): Boolean {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val result = Shell.cmd("pidof $packageName").exec()
        return result.isSuccess && result.out.isNotEmpty() && result.out.any {
            it.trim().isNotEmpty()
        }
    }

    fun getProcessInfo(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): Map<String, Any> {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val pidResult = Shell.cmd("pidof $packageName").exec()
        if (!pidResult.isSuccess || pidResult.out.isEmpty()) {
            return mapOf("running" to "false")
        }

        val pid = pidResult.out.firstOrNull()?.trim() ?: ""
        if (pid.isEmpty()) {
            return mapOf("running" to "false")
        }

        val memResult = Shell.cmd("cat /proc/$pid/status | grep -E 'VmSize|VmRSS|VmPeak'").exec()
        val cpuResult = Shell.cmd("cat /proc/$pid/stat").exec()

        val result = mutableMapOf(
            "running" to "true",
            "pid" to pid.toInt(),
            "cpu_stat_available" to cpuResult.isSuccess
        )

        if (memResult.isSuccess) {
            memResult.out.forEach { line ->
                val parts = line.split(":")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    if (key.isNotEmpty()) {
                        result[key] = value
                    }
                }
            }
        }

        return result
    }

    fun clearAppData(packageName: String, context: Context = JezailApp.Companion.appContext) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(packageName != context.packageName) { "Cannot clear data for self '${context.packageName}'" }

        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val result = Shell.cmd("pm clear $packageName").exec()
        require(result.isSuccess) {
            "Failed to clear data for '$packageName': ${
                result.err.joinToString("\n").ifEmpty { "Unknown error" }
            }"
        }
    }

    fun clearAppCache(packageName: String, context: Context = JezailApp.Companion.appContext) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val result = Shell.cmd("pm trim-caches 1000M").exec()
        val specificResult = Shell.cmd("rm -rf /data/data/$packageName/cache/*").exec()

        require(result.isSuccess || specificResult.isSuccess) {
            "Failed to clear cache for '$packageName': ${
                (result.err + specificResult.err).joinToString(
                    "\n"
                ).ifEmpty { "Unknown error" }
            }"
        }
    }

    fun getAppSignatures(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): Map<String, Any> {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrElse {
            error("Package '$packageName' not found")
        }

        val signatures = mutableListOf<Map<String, String>>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                val signatureArray = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }

                signatureArray?.forEach { signature ->
                    signatures.add(parseSignature(signature.toByteArray()))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.forEach { signature ->
                signatures.add(parseSignature(signature.toByteArray()))
            }
        }

        return mapOf(
            "packageName" to packageName,
            "signatures" to signatures,
            "signatureCount" to signatures.size,
        )
    }

    private fun parseSignature(signatureBytes: ByteArray): Map<String, String> {
        return try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificate = certificateFactory.generateCertificate(
                ByteArrayInputStream(signatureBytes)
            ) as X509Certificate

            mapOf(
                "md5" to getSignatureHash(signatureBytes, "MD5"),
                "sha1" to getSignatureHash(signatureBytes, "SHA1"),
                "sha256" to getSignatureHash(signatureBytes, "SHA-256"),
                "subject" to certificate.subjectDN.toString(),
                "issuer" to certificate.issuerDN.toString(),
                "serialNumber" to certificate.serialNumber.toString(),
                "notBefore" to certificate.notBefore.toString(),
                "notAfter" to certificate.notAfter.toString(),
                "algorithm" to certificate.sigAlgName,
                "version" to certificate.version.toString()
            )
        } catch (e: Exception) {
            mapOf(
                "md5" to getSignatureHash(signatureBytes, "MD5"),
                "sha1" to getSignatureHash(signatureBytes, "SHA1"),
                "sha256" to getSignatureHash(signatureBytes, "SHA-256"),
                "error" to "Failed to parse certificate: ${e.message}"
            )
        }
    }

    private fun getSignatureHash(signatureBytes: ByteArray, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val hash = digest.digest(signatureBytes)
            hash.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            "Error generating $algorithm hash for signature: ${e.message}"
        }
    }

    fun isAppDebuggable(
        packageName: String,
        context: Context = JezailApp.Companion.appContext
    ): Boolean {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }

        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val applicationInfo = packageInfo.applicationInfo
            (applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE)) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}