package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.FridaManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.fridaRoutes() {
    route("/frida", {
        description = "Frida dynamic instrumentation toolkit management endpoints"
    }) {
        get("/start", {
            description = "Start the Frida server"
        }) {
            call.respond(Success(data = FridaManager.start()))
        }

        get("/stop", {
            description = "Stop the Frida server"
        }) {
            call.respond(Success(data = FridaManager.stop()))
        }

        get("/status", {
            description = "Get the current status of the Frida server"
        }) {
            call.respond(Success(data = FridaManager.getStatus()))
        }

        get("/info", {
            description = "Get Frida installation and version information"
        }) {
            call.respond(Success(data = FridaManager.getInfo()))
        }

        get("/install", {
            description = "Install Frida on the device"
        }) {
            call.respond(Success(data = FridaManager.install(JezailApp.appContext)))
        }

        get("/update", {
            description = "Update Frida to the latest version"
        }) {
            call.respond(Success(data = FridaManager.updateToLatest(JezailApp.appContext)))
        }
    }
}