package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.api.Success
import com.azzahid.jezail.features.managers.AdbManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.adbRoutes() {
    route("/adb", {
        description = "ADB (Android Debug Bridge) management endpoints"
    }) {
        get("/start", {
            description = "Start the ADB server"
        }) {
            call.respond(Success(data = AdbManager.start()))
        }

        get("/stop", {
            description = "Stop the ADB server"
        }) {
            call.respond(Success(data = AdbManager.stop()))
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
            val data = AdbManager.installKey(publicKey)
            call.respond(Success(data = data))
        }

        get("/status", {
            description = "Get the current status of the ADB server"
        }) {
            call.respond(Success(data = AdbManager.getStatus()))
        }
    }
}