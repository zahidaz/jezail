package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.DeviceManager
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun Route.deviceRoutes() {
    route("/device") {
        val seLinuxMutex = Mutex()

        get({
            description =
                "Returns essential device information including hardware specs, Android version, root/security status, storage/RAM, battery level, and network connectivity for quick device profiling."
        }) {
            call.respond(Success(DeviceManager.getDeviceInfo()))
        }

        get("/build-info", {
            description = "Get device build information"
        }) {
            call.respond(Success(DeviceManager.getDeviceBuildInfo()))
        }

        get("/selinux", {
            description = "Get current SELinux status (Enforcing/Permissive)"
        }) { call.respond(Success(DeviceManager.getSELinuxStatus())) }

        post("/selinux/toggle", {
            description = "Toggle SELinux mode between Enforcing and Permissive"
        }) {
            seLinuxMutex.withLock {
                val currentStatus = DeviceManager.getSELinuxStatus().equals("Enforcing", true)
                DeviceManager.setSELinuxMode(!currentStatus)
                call.respond(Success(DeviceManager.getSELinuxStatus()))
            }
        }

        route("/clipboard") {
            get({
                description = "Get clipboard content and state"
            }) {
                val content = DeviceManager.getClipboard()
                call.respond(
                    Success(
                        mapOf(
                            "content" to content, "hasPrimary" to DeviceManager.hasPrimaryClip()
                        )
                    )
                )
            }

            post({
                description = "Set clipboard content"
                request {
                    queryParameter<String>("label") {
                        description = "Label for clipboard data"
                        required = false
                    }
                    body<String> {
                        description = "Clipboard text content"
                    }
                }
            }) {
                val text = call.receiveText()
                val label = call.request.queryParameters["label"] ?: "text"
                DeviceManager.setClipboard(label, text)
                call.respond(Success("Clipboard set"))
            }

            delete({
                description = "Clear clipboard content"
            }) {
                DeviceManager.clearClipboard()
                call.respond(Success("Clipboard cleared"))
            }
        }

        get("/battery", {
            description = "Get battery information"
        }) {
            call.respond(Success(DeviceManager.getBatteryInfo()))
        }

        get("/cpu", {
            description = "Get CPU information"
        }) {
            call.respond(Success(DeviceManager.getCpuInfo()))
        }

        get("/ram", {
            description = "Get RAM information"
        }) {
            call.respond(Success(DeviceManager.getRamInfo()))
        }

        get("/storage", {
            description = "Get basic storage usage information"
        }) {
            call.respond(Success(DeviceManager.getStorageInfoBasic()))
        }

        get("/storage/details", {
            description = "Get detailed storage usage information"
        }) {
            call.respond(Success(DeviceManager.getStorageInfo()))
        }

        get("/network", {
            description = "Get network status and information"
        }) {
            call.respond(Success(DeviceManager.getNetworkInfo()))
        }

        route("/logs") {
            get({
                description = "Get main logs"
                request {
                    queryParameter<Int>("lines") {
                        description = "Number of lines to fetch"
                        required = false
                    }
                    queryParameter<String>("filter") {
                        description = "Filter keyword for logs"
                        required = false
                    }
                }
            }) {
                val lines = call.request.queryParameters["lines"]?.toInt() ?: 100
                val filter = call.request.queryParameters["filter"]
                call.respond(Success(DeviceManager.getMainLogs(lines, filter)))
            }

            get("/kernel", {
                description = "Get kernel logs"
                request {
                    queryParameter<Int>("lines") { required = false }
                    queryParameter<String>("filter") { required = false }
                }
            }) {
                val lines = call.request.queryParameters["lines"]?.toInt() ?: 100
                val filter = call.request.queryParameters["filter"]
                call.respond(Success(DeviceManager.getKernelLogs(lines, filter)))
            }

            get("/radio", {
                description = "Get radio logs"
                request {
                    queryParameter<Int>("lines") { required = false }
                    queryParameter<String>("filter") { required = false }
                }
            }) {
                val lines = call.request.queryParameters["lines"]?.toInt() ?: 50
                val filter = call.request.queryParameters["filter"]
                call.respond(Success(DeviceManager.getRadioLogs(lines, filter)))
            }

            get("/system", {
                description = "Get system logs"
                request {
                    queryParameter<Int>("lines") { required = false }
                    queryParameter<String>("filter") { required = false }
                }
            }) {
                val lines = call.request.queryParameters["lines"]?.toInt() ?: 50
                val filter = call.request.queryParameters["filter"]
                call.respond(Success(DeviceManager.getSystemLogs(lines, filter)))
            }

            get("/crash", {
                description = "Get crash logs"
                request {
                    queryParameter<Int>("lines") { required = false }
                    queryParameter<String>("filter") { required = false }
                }
            }) {
                val lines = call.request.queryParameters["lines"]?.toInt() ?: 50
                val filter = call.request.queryParameters["filter"]
                call.respond(Success(DeviceManager.getCrashLogs(lines, filter)))
            }

            get("/events", {
                description = "Get event logs"
                request {
                    queryParameter<Int>("lines") { required = false }
                    queryParameter<String>("filter") { required = false }
                }
            }) {
                val lines = call.request.queryParameters["lines"]?.toInt() ?: 50
                val filter = call.request.queryParameters["filter"]
                call.respond(Success(DeviceManager.getEventsLogs(lines, filter)))
            }

            delete({
                description = "Clear logs for given buffer or all logs"
                request {
                    queryParameter<String>("buffer") {
                        description = "Log buffer type (main, system, radio, etc.)"
                        required = false
                    }
                }
            }) {
                val buffer = call.request.queryParameters["buffer"] ?: "all"
                val success = DeviceManager.clearLogs(buffer)
                if (success) {
                    call.respond(Success("Logs cleared"))
                } else {
                    call.respond(InternalServerError, Failure("Failed to clear logs"))
                }
            }
        }

        get("/screenshot", {
            description = "Capture a screenshot and return the file"
        }) {
            DeviceManager.captureScreenshot()?.let {
                if (it.exists()) {
                    call.response.header(
                        "Content-Disposition",
                        "attachment; filename=\"${it.name}\""
                    )
                    call.respondFile(it)
                } else {
                    call.respond(InternalServerError, Failure("Failed to capture screenshot"))
                }
            } ?: call.respond(InternalServerError, Failure("Failed to capture screenshot"))
        }

        route("/keys") {
            post("/home", { description = "Press the Home key" }) {
                val success = DeviceManager.pressHomeKey()
                call.respond(if (success) Success("Home key pressed") else Failure("Failed"))
            }

            post("/back", { description = "Press the Back key" }) {
                val success = DeviceManager.pressBackKey()
                call.respond(if (success) Success("Back key pressed") else Failure("Failed"))
            }

            post("/menu", { description = "Press the Menu key" }) {
                val success = DeviceManager.pressMenuKey()
                call.respond(if (success) Success("Menu key pressed") else Failure("Failed"))
            }

            post("/recent", { description = "Press the Recent Apps key" }) {
                val success = DeviceManager.pressRecentAppsKey()
                call.respond(if (success) Success("Recent apps key pressed") else Failure("Failed"))
            }

            post("/power", { description = "Press the Power key" }) {
                val success = DeviceManager.pressPowerKey()
                call.respond(if (success) Success("Power key pressed") else Failure("Failed"))
            }

            post("/volume-up", { description = "Press the Volume Up key" }) {
                val success = DeviceManager.pressVolumeUpKey()
                call.respond(if (success) Success("Volume up key pressed") else Failure("Failed"))
            }

            post("/volume-down", { description = "Press the Volume Down key" }) {
                val success = DeviceManager.pressVolumeDownKey()
                call.respond(if (success) Success("Volume down key pressed") else Failure("Failed"))
            }

            post("/volume-mute", { description = "Press the Mute key" }) {
                val success = DeviceManager.pressVolumeeMuteKey()
                call.respond(if (success) Success("Volume mute key pressed") else Failure("Failed"))
            }

            post("/volume-unmute", { description = "Unmute the volume" }) {
                val success = DeviceManager.pressVolumeUnmuteKey()
                call.respond(if (success) Success("Volume unmuted") else Failure("Failed"))
            }

            post("/keycode/{code}", {
                description = "Send a key event by keycode"
                request {
                    pathParameter<Int>("code") {
                        description = "Android keycode value"
                    }
                }
            }) {
                val keyCode = call.parameters["code"]?.toInt() ?: 0
                val success = DeviceManager.sendKeyCode(keyCode)
                call.respond(
                    if (success) Success("Key code $keyCode sent") else Failure("Failed to send")
                )
            }
        }

        route("/system") {
            get("/properties", {
                description = "Get all system properties"
            }) {
                call.respond(Success(DeviceManager.getAllSystemProperties()))
            }

            get("/properties/{key}", {
                description = "Get a specific system property"
                request {
                    pathParameter<String>("key") {
                        description = "System property key"
                    }
                }
            }) {
                val key = call.parameters["key"] ?: ""
                val value = DeviceManager.getSystemProperty(key)
                call.respond(Success(mapOf("key" to key, "value" to value)))
            }

            post("/properties/{key}", {
                description = "Set a specific system property"
                request {
                    pathParameter<String>("key") {
                        description = "System property key"
                    }
                    body<String> {
                        description = "System property value"
                    }
                }
            }) {
                val key = call.parameters["key"] ?: ""
                val value = call.receiveText()
                val success = DeviceManager.setSystemProperty(key, value)
                call.respond(if (success) Success("Property set") else Failure("Failed"))
            }
        }

        route("/processes") {
            get({
                description = "Get all running processes"
            }) {
                call.respond(Success(DeviceManager.getRunningProcesses()))
            }

            get("/{pid}", {
                description = "Get process information by PID"
                request {
                    pathParameter<Int>("pid") {
                        description = "Process ID"
                    }
                }
            }) {
                val pid = call.parameters["pid"]?.toInt() ?: 0
                call.respond(Success(DeviceManager.getProcessInfo(pid)))
            }

            delete("/{pid}", {
                description = "Kill process by PID"
                request {
                    pathParameter<Int>("pid") {
                        description = "Process ID"
                    }
                }
            }) {
                val pid = call.parameters["pid"]?.toInt() ?: 0
                val success = DeviceManager.killProcess(pid)
                call.respond(if (success) Success("Process killed") else Failure("Failed"))
            }

            delete("/name/{name}", {
                description = "Kill processes by name"
                request {
                    pathParameter<String>("name") {
                        description = "Process name"
                    }
                }
            }) {
                val name = call.parameters["name"] ?: ""
                val success = DeviceManager.killProcessByName(name)
                call.respond(if (success) Success("Processes killed") else Failure("Failed"))
            }
        }
    }
}



