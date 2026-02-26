package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.services.withRootFSFile
import com.azzahid.jezail.core.utils.zipDirectory
import com.azzahid.jezail.features.managers.MyPackageManager
import com.google.gson.Gson
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.util.UUID

fun Route.packageRoutes() {
    route("/package", {
        description = "Android package management endpoints"
    }) {
        get("/list", {
            description = "Get all installed applications"
        }) {
            call.respond(Success(MyPackageManager.getAllInstalledApps()))
        }

        get("/list/user", {
            description = "Get user-installed applications"
        }) {
            call.respond(Success(MyPackageManager.getUserInstalledApps()))
        }

        get("/list/system", {
            description = "Get system-installed applications"
        }) {
            call.respond(Success(MyPackageManager.getSystemInstalledApps()))
        }

        get("/{package}", {
            description = "Get simple details of a specific package"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            call.respond(Success(MyPackageManager.getAppSimpleDetails(pkg)))
        }

        get("/{package}/details", {
            description = "Get detailed information about a specific package"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            call.respond(Success(MyPackageManager.getAppDetails(pkg)))
        }

        get("/{package}/launch", {
            description = "Launch an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
                queryParameter<String>("activity") {
                    description = "Specific activity to launch (optional)"
                    required = false
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val activity = call.request.queryParameters["activity"]
            MyPackageManager.tryLaunchApp(pkg, activity)
            call.respond(Success("App launched successfully"))
        }

        get("/{package}/stop", {
            description = "Stop a running application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            MyPackageManager.tryStopApp(pkg)
            call.respond(Success("App stopped successfully"))
        }

        get("/{package}/foreground", {
            description = "Bring an application to the foreground"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
                queryParameter<Boolean>("launch") {
                    description = "Launch the app if not running (default: false)"
                    required = false
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val launch = call.request.queryParameters["launch"]?.toBoolean() ?: false
            call.respond(Success(MyPackageManager.bringToForeground(pkg, launch)))
        }

        get("/{package}/download", {
            description = "Download all APK files for a package"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val apkPaths = MyPackageManager.getApkPaths(pkg)
            check(apkPaths.isNotEmpty()) { "No APK files found for '$pkg'" }

            val downloadDir = File(
                JezailApp.appContext.getExternalFilesDir(null),
                "apk_download/${UUID.randomUUID()}"
            )
            downloadDir.mkdirs()

            try {
                apkPaths.forEach { path ->
                    withRootFSFile(path) { src ->
                        val target = File(downloadDir, File(path).name)
                        src.copyTo(target, overwrite = true)
                    }
                }

                val files = downloadDir.listFiles()?.toList() ?: emptyList()
                call.response.header("X-Apk-Count", files.size.toString())
                call.response.header("X-Apk-Files", files.joinToString(",") { it.name })
                call.response.header("X-Apk-Sizes", files.joinToString(",") { it.length().toString() })

                if (files.size == 1) {
                    val apkFile = files.first()
                    call.response.header(
                        "Content-Disposition",
                        "attachment; filename=\"$pkg.apk\""
                    )
                    call.respondFile(apkFile)
                } else {
                    val zipFile = zipDirectory(downloadDir, pkg)
                    call.response.header(
                        "Content-Disposition",
                        "attachment; filename=\"$pkg.zip\""
                    )
                    call.respondFile(zipFile)
                    zipFile.delete()
                }
            } finally {
                downloadDir.deleteRecursively()
            }
        }

        get("/{package}/backup", {
            description = "Create a full backup (APK + data) of an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val manifest = MyPackageManager.getBackupManifest(pkg)
            val apkPaths = MyPackageManager.getApkPaths(pkg)

            val backupDir = File(
                JezailApp.appContext.getExternalFilesDir(null),
                "backup/${UUID.randomUUID()}"
            )
            val apkDir = File(backupDir, "apk")
            apkDir.mkdirs()

            try {
                apkPaths.forEach { path ->
                    withRootFSFile(path) { src ->
                        val target = File(apkDir, File(path).name)
                        src.copyTo(target, overwrite = true)
                    }
                }

                val dataDir = manifest["dataDir"] as? String
                if (dataDir != null) {
                    val dataTar = File(backupDir, "data.tar.gz")
                    val tarResult = com.topjohnwu.superuser.Shell.cmd(
                        "tar czf '${dataTar.absolutePath}' -C /data/data '$pkg'"
                    ).exec()
                    if (!tarResult.isSuccess) {
                        val fallbackTar = File(backupDir, "data.tar")
                        com.topjohnwu.superuser.Shell.cmd(
                            "tar cf '${fallbackTar.absolutePath}' -C /data/data '$pkg'"
                        ).exec()
                    }
                }

                File(backupDir, "manifest.json").writeText(Gson().toJson(manifest))

                val zipFile = zipDirectory(backupDir, "$pkg-backup")
                call.response.header(
                    "Content-Disposition",
                    "attachment; filename=\"$pkg-backup.zip\""
                )
                call.respondFile(zipFile)
                zipFile.delete()
            } finally {
                backupDir.deleteRecursively()
            }
        }

        delete("/{package}", {
            description = "Uninstall an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            MyPackageManager.tryUninstallApp(pkg)
            call.respond(Success("App uninstalled successfully"))
        }

        post("/install", {
            description = "Install an APK, split APK zip, or XAPK application"
            request {
                queryParameter<Boolean>("forceInstall") {
                    description = "Force installation even if app already exists"
                    required = false
                }
                queryParameter<Boolean>("grantPermissions") {
                    description = "Automatically grant permissions during installation"
                    required = false
                }
                body<ByteArray> {
                    description = "Multipart form data containing the APK/XAPK file"
                }
            }
        }) {
            val forceInstall = call.request.queryParameters["forceInstall"]?.toBoolean() ?: false
            val grantPermissions =
                call.request.queryParameters["grantPermissions"]?.toBoolean() ?: false

            val tempFile = File.createTempFile("install_", ".bin", JezailApp.appContext.cacheDir)
            try {
                val multipart = call.receiveMultipart()

                var fileReceived = false
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val input = part.provider()
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        fileReceived = true
                        part.dispose()
                    }
                }

                require(fileReceived) { "No file provided in the request" }

                val header = ByteArray(2)
                tempFile.inputStream().use { it.read(header) }
                val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

                if (isZip) {
                    val hasManifest = try {
                        java.util.zip.ZipFile(tempFile).use { zip ->
                            zip.getEntry("manifest.json") != null
                        }
                    } catch (e: Exception) {
                        false
                    }

                    if (hasManifest) {
                        MyPackageManager.tryInstallXapk(tempFile, forceInstall, grantPermissions)
                    } else {
                        val extractDir = File(JezailApp.appContext.cacheDir, "splits_${System.currentTimeMillis()}")
                        try {
                            extractDir.mkdirs()
                            java.util.zip.ZipFile(tempFile).use { zip ->
                                zip.entries().asSequence()
                                    .filter { !it.isDirectory && it.name.endsWith(".apk") }
                                    .forEach { entry ->
                                        val outFile = File(extractDir, File(entry.name).name)
                                        zip.getInputStream(entry).use { input ->
                                            outFile.outputStream().use { output -> input.copyTo(output) }
                                        }
                                    }
                            }
                            val apks = extractDir.listFiles()?.filter { it.extension == "apk" } ?: emptyList()
                            check(apks.isNotEmpty()) { "No APK files found in zip" }
                            if (apks.size == 1) {
                                MyPackageManager.tryInstallApp(apks.first(), forceInstall, grantPermissions)
                            } else {
                                MyPackageManager.tryInstallMultiApk(apks, forceInstall, grantPermissions)
                            }
                        } finally {
                            extractDir.deleteRecursively()
                        }
                    }
                } else {
                    MyPackageManager.tryInstallApp(tempFile, forceInstall, grantPermissions)
                }

                call.respond(Success("App installed successfully"))
            } finally {
                tempFile.delete()
            }
        }

        post("/{package}/permissions/grant", {
            description = "Grant a permission to an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
                queryParameter<String>("permission") {
                    description = "Permission to grant (e.g., android.permission.CAMERA)"
                    required = true
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val permission = call.request.queryParameters["permission"]
                ?: throw IllegalArgumentException("Permission parameter is required")

            MyPackageManager.grantPermission(pkg, permission)
            call.respond(Success("Permission '$permission' granted to '$pkg'"))
        }

        post("/{package}/permissions/revoke", {
            description = "Revoke a permission from an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
                queryParameter<String>("permission") {
                    description = "Permission to revoke (e.g., android.permission.CAMERA)"
                    required = true
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val permission = call.request.queryParameters["permission"]
                ?: throw IllegalArgumentException("Permission parameter is required")

            MyPackageManager.revokePermission(pkg, permission)
            call.respond(Success("Permission '$permission' revoked from '$pkg'"))
        }

        get("/{package}/permissions", {
            description = "Get granted permissions for an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val granted = MyPackageManager.getGrantedPermissions(pkg)
            call.respond(Success(mapOf("granted" to granted)))
        }

        get("/{package}/permissions/all", {
            description = "Get all permissions (granted and denied) for an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val all = MyPackageManager.getAllPermissions(pkg)
            val granted = MyPackageManager.getGrantedPermissions(pkg)
            call.respond(
                Success(
                    mapOf(
                        "all" to all,
                        "granted" to granted,
                        "denied" to all.filterNot { it in granted }
                    )
                )
            )
        }

        get("/{package}/running", {
            description = "Check if an application is currently running"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val isRunning = MyPackageManager.isAppRunning(pkg)
            call.respond(Success(mapOf("running" to isRunning)))
        }

        get("/{package}/process-info", {
            description = "Get process information for an application"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val processInfo = MyPackageManager.getProcessInfo(pkg)
            call.respond(Success(processInfo))
        }

        post("/{package}/clear-data", {
            description = "Clear application data"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            MyPackageManager.clearAppData(pkg)
            call.respond(Success("Data cleared for '$pkg'"))
        }

        post("/{package}/clear-cache", {
            description = "Clear application cache"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            MyPackageManager.clearAppCache(pkg)
            call.respond(Success("Cache cleared for '$pkg'"))
        }

        get("/{package}/signatures", {
            description = "Get application signatures"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val signatures = MyPackageManager.getAppSignatures(pkg)
            call.respond(Success(signatures))
        }

        get("/{package}/debuggable", {
            description = "Check if an application is debuggable"
            request {
                pathParameter<String>("package") {
                    description = "Package name (e.g., com.example.app)"
                }
            }
        }) {
            val pkg = call.parameters.getOrFail("package")
            val isDebuggable = MyPackageManager.isAppDebuggable(pkg)
            call.respond(OK, mapOf("debuggable" to isDebuggable))
        }
    }
}
