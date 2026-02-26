package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.AdbManager
import com.azzahid.jezail.features.managers.DeviceManager
import com.azzahid.jezail.features.managers.ScreenMirrorManager
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
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.launch
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 100
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
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 100
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
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 50
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
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 50
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
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 50
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
                val lines = call.request.queryParameters["lines"]?.toIntOrNull() ?: 50
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

            webSocket("/live") {
                val buffer = call.request.queryParameters["buffer"] ?: "main"
                val filter = call.request.queryParameters["filter"]
                val process = DeviceManager.startLogcatProcess(buffer, filter)
                try {
                    val reader = process.inputStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        send(Frame.Text(line!!))
                    }
                } finally {
                    process.destroy()
                }
            }
        }

        webSocket("/mirror") {
            val interval = call.request.queryParameters["interval"]?.toLongOrNull() ?: 200L
            val quality = call.request.queryParameters["quality"]?.toIntOrNull()?.coerceIn(10, 100) ?: 50
            val scale = call.request.queryParameters["scale"]?.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: 0.5f
            while (true) {
                val bytes = if (ScreenMirrorManager.isActive) {
                    ScreenMirrorManager.latestFrame
                } else {
                    withContext(Dispatchers.IO) { DeviceManager.captureScreenshotBytes(quality, scale) }
                }
                if (bytes != null) send(Frame.Binary(true, bytes))
                delay(interval)
            }
        }

        get("/screenshot", {
            description = "Capture a screenshot and return the file"
        }) {
            val file = DeviceManager.captureScreenshot()
            if (file != null && file.exists()) {
                try {
                    call.response.header(
                        "Content-Disposition",
                        "attachment; filename=\"${file.name}\""
                    )
                    call.respondFile(file)
                } finally {
                    file.delete()
                }
            } else {
                call.respond(InternalServerError, Failure("Failed to capture screenshot"))
            }
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

            post("/text", {
                description = "Type text on the device"
                request {
                    body<String> { description = "Text to type" }
                }
            }) {
                val text = call.receiveText()
                require(text.isNotBlank()) { "Text cannot be blank" }
                val success = DeviceManager.inputText(text)
                call.respond(if (success) Success("Text input sent") else Failure("Failed"))
            }
        }

        route("/env") {
            get({
                description = "Get environment variables"
                request {
                    queryParameter<String>("filter") {
                        description = "Filter by key or value (case-insensitive)"
                        required = false
                    }
                    queryParameter<Boolean>("init") {
                        description = "If true, return init process (PID 1) environment"
                        required = false
                    }
                }
            }) {
                val init = call.request.queryParameters["init"]?.toBoolean() ?: false
                if (init) {
                    call.respond(Success(DeviceManager.getInitEnvironment()))
                } else {
                    val filter = call.request.queryParameters["filter"]
                    call.respond(Success(DeviceManager.getEnvironmentVariables(filter)))
                }
            }

            get("/{name}", {
                description = "Get a specific environment variable"
                request {
                    pathParameter<String>("name") {
                        description = "Environment variable name"
                    }
                }
            }) {
                val name = call.parameters["name"] ?: ""
                val value = DeviceManager.getEnvironmentVariable(name)
                call.respond(Success(mapOf("name" to name, "value" to value)))
            }
        }

        route("/proxy") {
            get({
                description = "Get current global proxy configuration"
            }) {
                call.respond(Success(DeviceManager.getProxy()))
            }

            post({
                description = "Set global HTTP proxy"
                request {
                    queryParameter<String>("host") {
                        description = "Proxy host"
                        required = true
                    }
                    queryParameter<Int>("port") {
                        description = "Proxy port"
                        required = true
                    }
                    queryParameter<String>("exclusionList") {
                        description = "Comma-separated list of hosts to exclude"
                        required = false
                    }
                }
            }) {
                val host = call.request.queryParameters["host"]
                    ?: throw IllegalArgumentException("host parameter is required")
                val port = call.request.queryParameters["port"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Valid port number is required")
                val exclusionList = call.request.queryParameters["exclusionList"]
                DeviceManager.setProxy(host, port, exclusionList)
                call.respond(Success(DeviceManager.getProxy()))
            }

            delete({
                description = "Clear global HTTP proxy"
            }) {
                DeviceManager.clearProxy()
                call.respond(Success("Proxy cleared"))
            }
        }

        route("/dns") {
            get({
                description = "Get current DNS configuration"
            }) {
                call.respond(Success(DeviceManager.getDnsConfig()))
            }

            post({
                description = "Set DNS servers"
                request {
                    queryParameter<String>("dns1") {
                        description = "Primary DNS server IP"
                        required = true
                    }
                    queryParameter<String>("dns2") {
                        description = "Secondary DNS server IP"
                        required = false
                    }
                }
            }) {
                val dns1 = call.request.queryParameters["dns1"]
                    ?: throw IllegalArgumentException("dns1 parameter is required")
                val dns2 = call.request.queryParameters["dns2"]
                DeviceManager.setDns(dns1, dns2)
                call.respond(Success(DeviceManager.getDnsConfig()))
            }

            delete({
                description = "Clear DNS servers"
            }) {
                DeviceManager.clearDns()
                call.respond(Success("DNS cleared"))
            }

            post("/private", {
                description = "Set private DNS hostname (Android 9+)"
                request {
                    queryParameter<String>("host") {
                        description = "Private DNS hostname"
                        required = true
                    }
                }
            }) {
                val host = call.request.queryParameters["host"]
                    ?: throw IllegalArgumentException("host parameter is required")
                DeviceManager.setPrivateDns(host)
                call.respond(Success(DeviceManager.getDnsConfig()))
            }

            delete("/private", {
                description = "Clear private DNS configuration"
            }) {
                DeviceManager.clearPrivateDns()
                call.respond(Success("Private DNS cleared"))
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

        route("/appops") {
            get("/{packageName}", {
                description = "Get app ops for a package"
                request {
                    pathParameter<String>("packageName") {
                        description = "Package name"
                    }
                }
            }) {
                val packageName = call.parameters["packageName"] ?: ""
                call.respond(Success(DeviceManager.getAppOps(packageName)))
            }

            post("/{packageName}/{op}/{mode}", {
                description = "Set an app op for a package"
                request {
                    pathParameter<String>("packageName") { description = "Package name" }
                    pathParameter<String>("op") { description = "Operation name (e.g. PROJECT_MEDIA)" }
                    pathParameter<String>("mode") { description = "Mode (allow, deny, ignore, default)" }
                }
            }) {
                val packageName = call.parameters["packageName"] ?: ""
                val op = call.parameters["op"] ?: ""
                val mode = call.parameters["mode"] ?: ""
                val success = DeviceManager.setAppOp(packageName, op, mode)
                call.respond(if (success) Success("App op set") else Failure("Failed"))
            }

            delete("/{packageName}", {
                description = "Reset all app ops for a package"
                request {
                    pathParameter<String>("packageName") { description = "Package name" }
                }
            }) {
                val packageName = call.parameters["packageName"] ?: ""
                val success = DeviceManager.resetAppOps(packageName)
                call.respond(if (success) Success("App ops reset") else Failure("Failed"))
            }
        }

        webSocket("/terminal") {
            val isAdbRunning = AdbManager.getStatus()["isRunning"] as Boolean
            if (!isAdbRunning) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "ADB is not running"))
                return@webSocket
            }
            val marker = "__JEZAIL_PWD__"
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val output = process.inputStream
            val input = process.outputStream
            withContext(Dispatchers.IO) {
                input.write("stty -echo 2>/dev/null; PS1=''; PS2=''\n".toByteArray())
                input.flush()
                input.write("echo ${marker}\$(pwd)${marker}\n".toByteArray())
                input.flush()
            }
            val outputJob = launch(Dispatchers.IO) {
                val buf = ByteArray(4096)
                var n: Int
                while (output.read(buf).also { n = it } != -1) {
                    send(Frame.Text(String(buf, 0, n)))
                }
            }
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val cmd = String(frame.data)
                        withContext(Dispatchers.IO) {
                            input.write(cmd.toByteArray())
                            input.write("echo ${marker}\$(pwd)${marker}\n".toByteArray())
                            input.flush()
                        }
                    }
                }
            } finally {
                process.destroy()
                outputJob.cancel()
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
