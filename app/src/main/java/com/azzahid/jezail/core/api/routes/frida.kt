package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.FridaManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.ServiceUnavailable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        }) {
            fridaRouteMutex.withLock {
                FridaManager.start()
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
    }
}