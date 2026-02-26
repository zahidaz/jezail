package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.AdbManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun Route.adbRoutes() {
    val adbRouteMutex = Mutex()
    route("/adb", {
        description = "ADB (Android Debug Bridge) management endpoints"
    }) {
        get("/start", {
            description = "Start the ADB server"
            request {
                queryParameter<Int>("port") {
                    description = "TCP port to start ADB on (optional, uses preferred port if not specified)"
                    required = false
                }
            }
        }) {
            adbRouteMutex.withLock {
                val port = call.request.queryParameters["port"]?.toIntOrNull()
                AdbManager.start(port)
                call.respond(Success(data = "ADB server started"))
            }
        }

        get("/stop", {
            description = "Stop the ADB server"
        }) {
            adbRouteMutex.withLock {
                AdbManager.stop()
                call.respond(Success(data = "ADB server stopped"))
            }
        }

        post("/key", {
            description = "Install a public key for ADB authentication"
            request {
                queryParameter<String>("publicKey") {
                    description = "The public key to install"
                    required = true
                }
            }
        }) {
            val publicKey = call.request.queryParameters["publicKey"].orEmpty()
            require(publicKey.isNotEmpty()) { "Missing publicKey parameter" }
            AdbManager.installKey(publicKey)
            call.respond(Success(data = "ADB key installed"))
        }

        get("/status", {
            description = "Get the current status of the ADB server"
        }) {
            call.respond(Success(data = AdbManager.getStatus()))
        }

        get("/port", {
            description = "Get the configured ADB port"
        }) {
            call.respond(Success(data = AdbManager.getPort()))
        }

        post("/port", {
            description = "Set the preferred ADB port"
            request {
                queryParameter<Int>("port") {
                    description = "TCP port number (1-65535)"
                    required = true
                }
            }
        }) {
            val port = call.request.queryParameters["port"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Valid port number is required")
            AdbManager.setPort(port)
            call.respond(Success(data = AdbManager.getPort()))
        }
    }
}
