package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.FridaManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.ServiceUnavailable
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Route.fridaRoutes() {
    val fridaRouteMutex = Mutex()
    route("/frida", {
        description = "Frida dynamic instrumentation toolkit management endpoints"
    }) {

        get("/status", {
            description = "Get the current status of the Frida server"
        }) {
            fridaRouteMutex.withLock {
                call.respond(Success(data = FridaManager.getStatus()))
            }
        }

        get("/info", {
            description = "Get Frida installation and version information"
        }) {
            fridaRouteMutex.withLock {
                call.respond(Success(data = FridaManager.getInfo()))
            }
        }

        get("/start", {
            description = "Start the Frida server"
            request {
                queryParameter<Int>("port") {
                    description = "Port to listen on (defaults to configured port)"
                    required = false
                }
            }
        }) {
            fridaRouteMutex.withLock {
                val port = call.request.queryParameters["port"]?.toIntOrNull()
                FridaManager.start(port)
                call.respond(Success(data = "Frida server started"))
            }
        }

        get("/stop", {
            description = "Stop the Frida server"
        }) {
            fridaRouteMutex.withLock {
                FridaManager.stop()
                call.respond(Success(data = "Frida server stopped"))
            }
        }


        get("/install", {
            description = "Install Frida on the device"
        }) {
            runCatching {

                fridaRouteMutex.withLock {
                    FridaManager.install(JezailApp.appContext)
                    call.respond(Success(data = FridaManager.getCurrentVersion()))
                }

            }.onFailure {
                call.respond(
                    status = ServiceUnavailable, Failure(error = it.message ?: "Unknown error")
                )
            }

        }

        get("/update", {
            description = "Update Frida to the latest version"
        }) {
            fridaRouteMutex.withLock {
                FridaManager.updateToLatest(JezailApp.appContext)
                call.respond(Success(data = FridaManager.getCurrentVersion()))
            }
        }

        get("/config", {
            description = "Get Frida server configuration"
        }) {
            call.respond(Success(mapOf(
                "port" to Preferences.fridaPort,
                "binaryName" to Preferences.fridaBinaryName
            )))
        }

        post("/config", {
            description = "Update Frida server configuration"
            request {
                body<String> { description = "JSON with 'port' (int) and/or 'binaryName' (string)" }
            }
        }) {
            val body = Json.parseToJsonElement(call.receiveText()).jsonObject
            body["port"]?.jsonPrimitive?.intOrNull?.let { Preferences.fridaPort = it }
            body["binaryName"]?.jsonPrimitive?.content?.let { Preferences.fridaBinaryName = it }
            call.respond(Success(mapOf(
                "port" to Preferences.fridaPort,
                "binaryName" to Preferences.fridaBinaryName
            )))
        }
    }
}