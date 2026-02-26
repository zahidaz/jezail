package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.topjohnwu.superuser.Shell
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun sanitizeShellArg(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

private fun buildExtrasArgs(extras: Map<String, String>): String =
    extras.entries.joinToString(" ") { (key, value) ->
        "--es ${sanitizeShellArg(key)} ${sanitizeShellArg(value)}"
    }

private fun parseIntentJson(text: String): Map<String, Any?> {
    val json = Json.parseToJsonElement(text).jsonObject
    return mapOf(
        "action" to json["action"]?.jsonPrimitive?.content,
        "package" to json["package"]?.jsonPrimitive?.content,
        "class" to json["class"]?.jsonPrimitive?.content,
        "data" to json["data"]?.jsonPrimitive?.content,
        "extras" to json["extras"]?.jsonObject?.map { it.key to it.value.jsonPrimitive.content }?.toMap(),
        "flags" to json["flags"]?.jsonArray?.map { it.jsonPrimitive.content }
    )
}

private fun buildAmCommand(base: String, intent: Map<String, Any?>): String {
    val parts = mutableListOf(base)

    (intent["action"] as? String)?.let { parts.add("-a ${sanitizeShellArg(it)}") }
    (intent["data"] as? String)?.let { parts.add("-d ${sanitizeShellArg(it)}") }

    val pkg = intent["package"] as? String
    val cls = intent["class"] as? String
    if (pkg != null && cls != null) {
        parts.add("-n ${sanitizeShellArg("$pkg/$cls")}")
    } else if (pkg != null) {
        parts.add("-p ${sanitizeShellArg(pkg)}")
    }

    @Suppress("UNCHECKED_CAST")
    (intent["flags"] as? List<String>)?.forEach { flag ->
        val flagValue = intentFlags[flag]
        if (flagValue != null) parts.add("-f $flagValue")
    }

    @Suppress("UNCHECKED_CAST")
    (intent["extras"] as? Map<String, String>)?.let {
        if (it.isNotEmpty()) parts.add(buildExtrasArgs(it))
    }

    return parts.joinToString(" ")
}

private val intentFlags = mapOf(
    "FLAG_ACTIVITY_NEW_TASK" to "0x10000000",
    "FLAG_ACTIVITY_CLEAR_TOP" to "0x04000000",
    "FLAG_ACTIVITY_SINGLE_TOP" to "0x20000000",
    "FLAG_ACTIVITY_NO_HISTORY" to "0x40000000",
    "FLAG_ACTIVITY_CLEAR_TASK" to "0x00008000",
    "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS" to "0x00800000",
    "FLAG_ACTIVITY_FORWARD_RESULT" to "0x02000000",
    "FLAG_ACTIVITY_MULTIPLE_TASK" to "0x08000000",
    "FLAG_ACTIVITY_NO_ANIMATION" to "0x00010000",
    "FLAG_ACTIVITY_REORDER_TO_FRONT" to "0x00020000"
)

private suspend fun executeAmCommand(command: String): Pair<Boolean, String> =
    withContext(Dispatchers.IO) {
        val result = Shell.cmd(command).exec()
        if (result.isSuccess) {
            true to (result.out.joinToString("\n").ifEmpty { "OK" })
        } else {
            false to result.err.joinToString("\n").ifEmpty { "Command failed" }
        }
    }

fun Route.intentRoutes() {
    route("/intent", {
        description = "Android intent management endpoints for sending activities, broadcasts, and services"
    }) {
        post("/start-activity", {
            description = "Start an Android activity with the specified intent parameters"
            request {
                body<String> {
                    description = "JSON with action, package, class, data, extras, and flags"
                }
            }
        }) {
            val intent = parseIntentJson(call.receiveText())
            val command = buildAmCommand("am start", intent)
            val (success, output) = executeAmCommand(command)
            if (success) call.respond(Success("Activity started"))
            else call.respond(InternalServerError, Failure(output))
        }

        post("/broadcast", {
            description = "Send an Android broadcast with the specified intent parameters"
            request {
                body<String> {
                    description = "JSON with action, package, and extras"
                }
            }
        }) {
            val intent = parseIntentJson(call.receiveText())
            val command = buildAmCommand("am broadcast", intent)
            val (success, output) = executeAmCommand(command)
            if (success) call.respond(Success("Broadcast sent"))
            else call.respond(InternalServerError, Failure(output))
        }

        post("/start-service", {
            description = "Start an Android service with the specified intent parameters"
            request {
                body<String> {
                    description = "JSON with action, package, class, and extras"
                }
            }
        }) {
            val intent = parseIntentJson(call.receiveText())
            val command = buildAmCommand("am startservice", intent)
            val (success, output) = executeAmCommand(command)
            if (success) call.respond(Success("Service started"))
            else call.respond(InternalServerError, Failure(output))
        }
    }
}
