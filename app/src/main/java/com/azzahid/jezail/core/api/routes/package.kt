package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.PackageManager
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File

fun Route.packageRoutes() {
    route("/package", {
        description = "Android package management endpoints"
    }) {
        get("/list", {
            description = "Get all installed applications"
        }) {
            call.respond(Success(PackageManager.getAllInstalledApps()))
        }

        get("/list/user", {
            description = "Get user-installed applications"
        }) {
            call.respond(Success(PackageManager.getUserInstalledApps()))
        }

        get("/list/system", {
            description = "Get system-installed applications"
        }) {
            call.respond(Success(PackageManager.getSystemInstalledApps()))
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
            call.respond(Success(PackageManager.getAppSimpleDetails(pkg)))
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
            call.respond(Success(PackageManager.getAppDetails(pkg)))
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
            PackageManager.tryLaunchApp(pkg, activity)
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
            PackageManager.tryStopApp(pkg)
            call.respond(Success("App stopped successfully"))
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
            PackageManager.tryUninstallApp(pkg)
            call.respond(Success("App uninstalled successfully"))
        }

        post("/install", {
            description = "Install an APK application"
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
                    description = "Multipart form data containing the APK file"
                }
            }
        }) {
            val forceInstall = call.request.queryParameters["forceInstall"]?.toBoolean() ?: false
            val grantPermissions = call.request.queryParameters["grantPermissions"]?.toBoolean() ?: false

            val tempFile = File.createTempFile("apk_", ".apk", JezailApp.appContext.cacheDir)
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

            require(fileReceived) { "No APK file provided in the request" }

            try {
                PackageManager.tryInstallApp(
                    apk = tempFile,
                    forceInstall = forceInstall,
                    grantPermissions = grantPermissions,
                )
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

            PackageManager.grantPermission(pkg, permission)
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

            PackageManager.revokePermission(pkg, permission)
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
            val granted = PackageManager.getGrantedPermissions(pkg)
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
            val all = PackageManager.getAllPermissions(pkg)
            val granted = PackageManager.getGrantedPermissions(pkg)
            call.respond(
                Success(
                    mapOf(
                        "all" to all,
                        "granted" to granted,
                        "denied" to all.filterNot { it in granted })
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
            val isRunning = PackageManager.isAppRunning(pkg)
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
            val processInfo = PackageManager.getProcessInfo(pkg)
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
            PackageManager.clearAppData(pkg)
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
            PackageManager.clearAppCache(pkg)
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
            val signatures = PackageManager.getAppSignatures(pkg)
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
            val isDebuggable = PackageManager.isAppDebuggable(pkg)
            call.respond(OK, mapOf("debuggable" to isDebuggable))
        }
    }
}