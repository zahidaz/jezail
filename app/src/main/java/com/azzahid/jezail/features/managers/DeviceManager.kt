package com.azzahid.jezail.features.managers

import android.Manifest.permission.READ_PHONE_STATE
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N_MR1
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat.checkSelfPermission
import com.azzahid.jezail.JezailApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile


object DeviceManager {

    internal val GETPROP_REGEX = Regex("\\[(.+?)]: \\[(.*)]")
    internal val WHITESPACE_REGEX = Regex("\\s+")

    fun getDeviceInfo(): Map<String, Any?> {
        val ram = getRamInfo()
        val battery = getBatteryInfo()
        val network = getNetworkInfo()
        return mapOf(
            "deviceName" to getUserDefinedDeviceName(),
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "androidVersion" to Build.VERSION.RELEASE,
            "sdkInt" to SDK_INT,
            "securityPatch" to Build.VERSION.SECURITY_PATCH,
            "fingerprint" to Build.FINGERPRINT,
            "isRooted" to Shell.isAppGrantedRoot(),
            "seLinuxStatus" to getSELinuxStatus(),
            "isDebuggable" to (Build.TYPE == "userdebug" || Build.TYPE == "eng"),
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
            "is64Bit" to Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            "totalRam" to ram["totalMem"],
            "availableRam" to ram["availMem"],
            "internalStorage" to getStorageInfoBasic()["internal"],
            "batteryLevel" to battery["level"],
            "isCharging" to battery["plugged"],
            "hasInternet" to (network["activeConnection"] as? Map<*, *>)?.get("hasInternet"),
            "wifiSSID" to (network["wifi"] as? Map<*, *>)?.get("ssid")
        )
    }

    fun getUserDefinedDeviceName(contentResolver: ContentResolver = JezailApp.Companion.appContext.contentResolver): String? {
        return if (SDK_INT >= N_MR1) {
            Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
        } else {
            null
        } ?: Settings.Secure.getString(contentResolver, "bluetooth_name")
        ?: Settings.System.getString(contentResolver, "device_name")
    }


    fun getDeviceBuildInfo(): Map<String, Any?> {
        return mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "hardware" to Build.HARDWARE,
            "sdkInt" to SDK_INT,
            "androidVersion" to Build.VERSION.RELEASE,
            "securityPatch" to Build.VERSION.SECURITY_PATCH,
            "codename" to Build.VERSION.CODENAME,
            "buildType" to Build.TYPE,
            "buildTags" to Build.TAGS,
            "fingerprint" to Build.FINGERPRINT,
            "buildId" to Build.ID,
            "display" to Build.DISPLAY,
            "bootloader" to Build.BOOTLOADER,
            "radio" to Build.getRadioVersion(),
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
            "board" to Build.BOARD,
            "host" to Build.HOST,
            "user" to Build.USER,
            "time" to Build.TIME,
            "isDebuggable" to (Build.TYPE == "userdebug" || Build.TYPE == "eng"),
        )
    }

    fun getSELinuxStatus(): String {
        return Shell.cmd("getenforce").exec().out.first()
    }

    fun setSELinuxMode(enforcing: Boolean): Boolean {
        return try {
            val command = if (enforcing) "setenforce 1" else "setenforce 0"
            Shell.cmd(command).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    fun getClipboard(context: Context = JezailApp.Companion.appContext): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    fun setClipboard(
        label: String,
        text: String,
        context: Context = JezailApp.Companion.appContext
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun clearClipboard(context: Context = JezailApp.Companion.appContext) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun hasPrimaryClip(context: Context = JezailApp.Companion.appContext): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.hasPrimaryClip()
    }

    fun getBatteryInfo(context: Context = JezailApp.Companion.appContext): Map<String, Any?> {
        val batteryIntent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        return mapOf(
            "level" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            "scale" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            "status" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            "health" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
            "plugged" to (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0),
            "present" to (batteryIntent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
                ?: false),
            "technology" to batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            "temperature" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1),
            "pluggedTypes" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
            "voltage" to batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
        )
    }

    fun getCpuInfo(): Map<String, Any?> {
        val cores = Runtime.getRuntime().availableProcessors()
        val cpuInfo = Shell.cmd("cat /proc/cpuinfo").exec()
        val cpuFreq = Shell.cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").exec()

        val cpuDetails = if (cpuInfo.isSuccess) {
            cpuInfo.out.mapNotNull { line ->
                val parts = line.split(":")
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap()
        } else emptyMap()

        return mapOf(
            "cores" to cores,
            "architecture" to System.getProperty("os.arch"),
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
            "is64Bit" to Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            "processor" to cpuDetails["processor"],
            "hardware" to cpuDetails["Hardware"],
            "revision" to cpuDetails["Revision"],
            "maxFreq" to if (cpuFreq.isSuccess) cpuFreq.out.firstOrNull()?.toLongOrNull() else null,
            "features" to cpuDetails["Features"]?.split(" ")
        )
    }

    suspend fun getCpuUsageMap(): Map<String, Float> = withContext(Dispatchers.IO) {
        try {
            RandomAccessFile("/proc/stat", "r").use { reader ->
                var load = reader.readLine()
                var toks = load.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                val idle1 = toks[3].toLong()
                val cpu1 = toks[1].toLong() + toks[2].toLong() + toks[4].toLong() + toks[5].toLong() + toks[6].toLong() + toks[7].toLong()
                delay(360)
                reader.seek(0)
                load = reader.readLine()
                toks = load.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                val idle2 = toks[3].toLong()
                val cpu2 = toks[1].toLong() + toks[2].toLong() + toks[4].toLong() + toks[5].toLong() + toks[6].toLong() + toks[7].toLong()
                val cpuUsage = (cpu2 - cpu1).toFloat() / ((cpu2 + idle2) - (cpu1 + idle1))
                mapOf("cpu_usage" to cpuUsage)
            }
        } catch (ex: Exception) {
            mapOf("cpu_usage" to 0f)
        }
    }


    fun getRamInfo(): Map<String, Any?> {
        val activityManager =
            JezailApp.Companion.appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val meminfoResult = Shell.cmd("cat /proc/meminfo").exec()
        val memDetails = if (meminfoResult.isSuccess) {
            meminfoResult.out.mapNotNull { line ->
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().replace(" kB", "").toLongOrNull()?.times(1024)
                    key to value
                } else null
            }.toMap()
        } else emptyMap()

        return mapOf(
            "totalMem" to memInfo.totalMem,
            "availMem" to memInfo.availMem,
            "usedMem" to (memInfo.totalMem - memInfo.availMem),
            "lowMemory" to memInfo.lowMemory,
            "isLowRamDevice" to activityManager.isLowRamDevice,
            "threshold" to memInfo.threshold,
            "memTotal" to memDetails["MemTotal"],
            "memFree" to memDetails["MemFree"],
            "memAvailable" to memDetails["MemAvailable"],
            "cached" to memDetails["Cached"],
            "buffers" to memDetails["Buffers"],
            "swapTotal" to memDetails["SwapTotal"],
            "swapFree" to memDetails["SwapFree"],
            "shmem" to memDetails["Shmem"]
        )
    }

    fun getStorageInfoBasic(): Map<String, Any?> {
        val internalStorage = Environment.getDataDirectory()
        val externalStorage = Environment.getExternalStorageDirectory()
        val rootStorage = File("/")
        val systemStorage = File("/system")
        val dataStorage = File("/data")
        val sdcardStorage = File(Environment.getExternalStorageDirectory().path)
        val tmpStorage = File("/tmp")
        val varStorage = File("/var")

        fun getBasicStorageDetails(file: File) = mapOf(
            "totalSpace" to file.totalSpace,
            "freeSpace" to file.freeSpace,
            "usedSpace" to (file.totalSpace - file.freeSpace),
            "path" to file.absolutePath,
            "writable" to file.canWrite(),
            "readable" to file.canRead(),
            "exists" to file.exists()
        )

        return mapOf(
            "internal" to getBasicStorageDetails(internalStorage),
            "external" to getBasicStorageDetails(externalStorage),
            "root" to getBasicStorageDetails(rootStorage),
            "system" to getBasicStorageDetails(systemStorage),
            "data" to getBasicStorageDetails(dataStorage),
            "sdcard" to getBasicStorageDetails(sdcardStorage),
            "tmp" to getBasicStorageDetails(tmpStorage),
            "var" to getBasicStorageDetails(varStorage)
        )
    }

    fun getStorageInfo(): Map<String, Any?> {
        val internalStorage = Environment.getDataDirectory()
        val externalStorage = Environment.getExternalStorageDirectory()
        val rootStorage = File("/")
        val systemStorage = File("/system")
        val dataStorage = File("/data")
        val cacheStorage = File("/cache")

        fun getStorageDetails(file: File) = mapOf(
            "totalSpace" to file.totalSpace,
            "freeSpace" to file.freeSpace,
            "usableSpace" to file.usableSpace,
            "usedSpace" to (file.totalSpace - file.freeSpace),
            "path" to file.absolutePath,
            "writable" to file.canWrite(),
            "readable" to file.canRead()
        )

        return mapOf(
            "internal" to getStorageDetails(internalStorage),
            "external" to getStorageDetails(externalStorage),
            "root" to getStorageDetails(rootStorage),
            "system" to getStorageDetails(systemStorage),
            "data" to getStorageDetails(dataStorage),
            "cache" to if (cacheStorage.exists()) getStorageDetails(cacheStorage) else null,
            "mountPoints" to getMountPoints(),
            "diskStats" to getDiskStats()
        )
    }

    private fun getMountPoints(): List<Map<String, String?>> {
        val mountsResult = Shell.cmd("cat /proc/mounts").exec()
        return if (mountsResult.isSuccess) {
            mountsResult.out.mapNotNull { line ->
                val parts = line.split(" ")
                if (parts.size >= 4) {
                    mapOf(
                        "device" to parts[0],
                        "mountPoint" to parts[1],
                        "fileSystem" to parts[2],
                        "options" to parts[3]
                    )
                } else null
            }
        } else emptyList()
    }

    private fun getDiskStats(): Map<String, Any?> {
        val diskstatsResult = Shell.cmd("cat /proc/diskstats").exec()
        return if (diskstatsResult.isSuccess) {
            mapOf(
                "blockDevices" to diskstatsResult.out.mapNotNull { line ->
                    val parts = line.trim().split(WHITESPACE_REGEX)
                    if (parts.size >= 14) {
                        mapOf<String, Any?>(
                            "device" to parts[2],
                            "readsCompleted" to parts[3].toLongOrNull(),
                            "sectorsRead" to parts[5].toLongOrNull(),
                            "writesCompleted" to parts[7].toLongOrNull(),
                            "sectorsWritten" to parts[9].toLongOrNull()
                        )
                    } else null
                })
        } else emptyMap()
    }


    fun getNetworkInfo(context: Context = JezailApp.appContext): Map<String, Any?> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val an = cm.activeNetwork
        val nc = cm.getNetworkCapabilities(an)
        val lp = cm.getLinkProperties(an)
        val wi = if (SDK_INT >= Build.VERSION_CODES.S) {
            nc?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION") wm.connectionInfo
        }
        val ifRes = runCatching { Shell.cmd("ip addr show").exec() }.getOrNull()
        val rtRes = runCatching { Shell.cmd("ip route show").exec() }.getOrNull()
        val arpRes = runCatching { Shell.cmd("cat /proc/net/arp").exec() }.getOrNull()

        return buildMap {
            put(
                "activeConnection", mapOf(
                    "hasInternet" to (nc?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        ?: false),
                    "hasWifi" to (nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false),
                    "hasCellular" to (nc?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        ?: false),
                    "hasEthernet" to (nc?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        ?: false),
                    "validated" to (nc?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        ?: false)
                )
            )
            put(
                "wifi", mapOf(
                    "ssid" to wi?.ssid?.removeSurrounding("\""),
                    "bssid" to wi?.bssid?.takeIf { it != "02:00:00:00:00:00" },
                    "ipAddress" to if (SDK_INT >= Build.VERSION_CODES.S)
                        lp?.linkAddresses?.firstOrNull { !it.address.isLoopbackAddress }?.address?.hostAddress
                    else @Suppress("DEPRECATION")
                    wi?.ipAddress?.let { intToIp(it) },
                    "macAddress" to wi?.macAddress?.takeIf { it != "02:00:00:00:00:00" },
                    "networkId" to wi?.networkId,
                    "rssi" to wi?.rssi,
                    "linkSpeed" to wi?.linkSpeed,
                    "frequency" to wi?.frequency,
                    "txLinkSpeed" to if (SDK_INT >= Build.VERSION_CODES.Q) wi?.txLinkSpeedMbps else null,
                    "rxLinkSpeed" to if (SDK_INT >= Build.VERSION_CODES.Q) wi?.rxLinkSpeedMbps else null
                )
            )
            put(
                "dhcp", if (SDK_INT >= Build.VERSION_CODES.S) {
                    mapOf(
                        "ipAddress" to lp?.linkAddresses?.firstOrNull { !it.address.isLoopbackAddress }?.address?.hostAddress,
                        "gateway" to lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress,
                        "dnsServers" to lp?.dnsServers?.map { it.hostAddress },
                        "domains" to lp?.domains,
                        "mtu" to lp?.mtu
                    )
                } else {
                    @Suppress("DEPRECATION") val di = wm.dhcpInfo
                    mapOf(
                        "ipAddress" to intToIp(di.ipAddress),
                        "gateway" to intToIp(di.gateway),
                        "netmask" to intToIp(di.netmask),
                        "dns1" to intToIp(di.dns1),
                        "dns2" to intToIp(di.dns2),
                        "serverAddress" to intToIp(di.serverAddress),
                        "leaseDuration" to di.leaseDuration
                    )
                }
            )
            put(
                "linkProperties", mapOf(
                    "interfaceName" to lp?.interfaceName,
                    "linkAddresses" to lp?.linkAddresses?.map { it.toString() },
                    "routes" to lp?.routes?.map { it.toString() },
                    "dnsServers" to lp?.dnsServers?.map { it.hostAddress },
                    "domains" to lp?.domains
                )
            )
            if (checkSelfPermission(context, READ_PHONE_STATE) == PERMISSION_GRANTED) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                put(
                    "cellular", mapOf(
                        "networkOperator" to tm.networkOperatorName,
                        "networkType" to if (SDK_INT >= Build.VERSION_CODES.R) tm.dataNetworkType else tm.networkType,
                        "phoneType" to tm.phoneType,
                        "simState" to tm.simState,
                        "isNetworkRoaming" to tm.isNetworkRoaming,
                        "isDataEnabled" to if (SDK_INT >= Build.VERSION_CODES.O) tm.isDataEnabled else null
                    )
                )
            }
            put("interfaces", ifRes?.takeIf { it.isSuccess }?.out ?: emptyList<Any>())
            put("routes", rtRes?.takeIf { it.isSuccess }?.out ?: emptyList<Any>())
            put("arp", arpRes?.takeIf { it.isSuccess }?.out ?: emptyList<Any>())
        }
    }


    private fun intToIp(ip: Int): String =
        "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"

    internal fun sanitizeShellArg(input: String): String =
        input.replace("'", "'\\''")

    private fun getFilteredLogs(baseCommand: String, lines: Int, filter: String?): List<String> {
        val command = if (filter != null) {
            "$baseCommand | grep '${sanitizeShellArg(filter)}'"
        } else {
            baseCommand
        }
        val result = Shell.cmd(command).exec()
        return if (result.isSuccess) result.out else emptyList()
    }

    fun getMainLogs(lines: Int = 100, filter: String? = null): List<String> =
        getFilteredLogs("logcat -d -t $lines", lines, filter)

    fun getKernelLogs(lines: Int = 100, filter: String? = null): List<String> =
        getFilteredLogs("dmesg | tail -$lines", lines, filter)

    fun getRadioLogs(lines: Int = 50, filter: String? = null): List<String> =
        getFilteredLogs("logcat -b radio -d -t $lines", lines, filter)

    fun getSystemLogs(lines: Int = 50, filter: String? = null): List<String> =
        getFilteredLogs("logcat -b system -d -t $lines", lines, filter)

    fun getCrashLogs(lines: Int = 50, filter: String? = null): List<String> =
        getFilteredLogs("logcat -b crash -d -t $lines", lines, filter)

    fun getEventsLogs(lines: Int = 50, filter: String? = null): List<String> =
        getFilteredLogs("logcat -b events -d -t $lines", lines, filter)

    fun clearLogs(buffer: String = "all"): Boolean {
        val sanitized = sanitizeShellArg(buffer)
        val command = if (buffer == "all") "logcat -c" else "logcat -b '${sanitized}' -c"
        val result = Shell.cmd(command).exec()
        return result.isSuccess
    }

    fun captureScreenshot(): File? {
        val f = "screenshot_${System.currentTimeMillis()}.png"
        val destination = File(JezailApp.Companion.appContext.cacheDir, f)
        val res = Shell.cmd("screencap -p ${destination.absolutePath}").exec()
        return if (res.isSuccess) destination else null
    }

    fun pressHomeKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_HOME").exec()
        return result.isSuccess
    }

    fun pressBackKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_BACK").exec()
        return result.isSuccess
    }

    fun pressMenuKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_MENU").exec()
        return result.isSuccess
    }

    fun pressRecentAppsKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_APP_SWITCH").exec()
        return result.isSuccess
    }

    fun pressPowerKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_POWER").exec()
        return result.isSuccess
    }

    fun pressVolumeUpKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_VOLUME_UP").exec()
        return result.isSuccess
    }

    fun pressVolumeDownKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_VOLUME_DOWN").exec()
        return result.isSuccess
    }

    fun pressVolumeeMuteKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_VOLUME_MUTE").exec()
        return result.isSuccess
    }

    fun pressVolumeUnmuteKey(): Boolean {
        val result = Shell.cmd("input keyevent KEYCODE_VOLUME_UP").exec()
        return result.isSuccess
    }

    fun sendKeyCode(keyCode: Int): Boolean {
        val result = Shell.cmd("input keyevent $keyCode").exec()
        return result.isSuccess
    }

    fun getSystemProperty(key: String): String? {
        val result = Shell.cmd("getprop '${sanitizeShellArg(key)}'").exec()
        return if (result.isSuccess) result.out.firstOrNull() else null
    }

    fun setSystemProperty(key: String, value: String): Boolean {
        val result = Shell.cmd("setprop '${sanitizeShellArg(key)}' '${sanitizeShellArg(value)}'").exec()
        return result.isSuccess
    }

    fun getAllSystemProperties(): Map<String, String> {
        val result = Shell.cmd("getprop").exec()
        return if (result.isSuccess) {
            result.out.mapNotNull { line ->
                GETPROP_REGEX.find(line)?.let { match ->
                    match.groupValues[1] to match.groupValues[2]
                }
            }.toMap()
        } else emptyMap()
    }

    fun getRunningProcesses(): List<Map<String, Any?>> {
        val result = Shell.cmd("ps -A").exec()
        return if (result.isSuccess) {
            result.out.drop(1).mapNotNull { line ->
                val parts = line.trim().split(WHITESPACE_REGEX)
                if (parts.size >= 9) {
                    mapOf<String, Any?>(
                        "pid" to parts[1].toIntOrNull(),
                        "ppid" to parts[2].toIntOrNull(),
                        "user" to parts[0],
                        "vsz" to parts[4].toLongOrNull(),
                        "rss" to parts[5].toLongOrNull(),
                        "wchan" to parts[6],
                        "addr" to parts[7],
                        "stat" to parts[8],
                        "name" to parts.drop(8).joinToString(" ")
                    )
                } else null
            }
        } else emptyList()
    }

    fun killProcess(pid: Int): Boolean {
        val result = Shell.cmd("kill $pid").exec()
        return result.isSuccess
    }

    fun killProcessByName(name: String): Boolean {
        val result = Shell.cmd("pkill '${sanitizeShellArg(name)}'").exec()
        return result.isSuccess
    }

    fun getEnvironmentVariables(filter: String? = null): Map<String, String> {
        val result = Shell.cmd("env").exec()
        if (!result.isSuccess) return emptyMap()
        return result.out.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1) else null
        }.filter { (key, value) ->
            filter == null || key.contains(filter, ignoreCase = true) || value.contains(filter, ignoreCase = true)
        }.toMap()
    }

    fun getEnvironmentVariable(name: String): String? {
        val result = Shell.cmd("printenv '${sanitizeShellArg(name)}'").exec()
        return if (result.isSuccess) result.out.firstOrNull() else null
    }

    fun getInitEnvironment(): Map<String, String> {
        val result = Shell.cmd("cat /proc/1/environ").exec()
        if (!result.isSuccess) return emptyMap()
        return result.out.joinToString("").split('\u0000').mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
        }.toMap()
    }

    fun getProxy(): Map<String, Any?> {
        val proxy = Shell.cmd("settings get global http_proxy").exec()
            .out.firstOrNull()?.takeIf { it != "null" && it.isNotBlank() }
        val exclusionList = Shell.cmd("settings get global global_http_proxy_exclusion_list").exec()
            .out.firstOrNull()?.takeIf { it != "null" && it.isNotBlank() }
        val host = proxy?.substringBefore(":")
        val port = proxy?.substringAfter(":", "")?.toIntOrNull()
        return mapOf(
            "enabled" to (proxy != null),
            "host" to host,
            "port" to port,
            "exclusionList" to exclusionList
        )
    }

    fun setProxy(host: String, port: Int, exclusionList: String? = null) {
        val sanitizedHost = sanitizeShellArg(host)
        Shell.cmd("settings put global http_proxy '${sanitizedHost}:${port}'").exec()
        if (exclusionList != null) {
            Shell.cmd("settings put global global_http_proxy_exclusion_list '${sanitizeShellArg(exclusionList)}'").exec()
        }
    }

    fun clearProxy() {
        Shell.cmd("settings delete global http_proxy").exec()
        Shell.cmd("settings delete global global_http_proxy_exclusion_list").exec()
    }

    fun getDnsConfig(): Map<String, Any?> {
        val dns1 = Shell.cmd("getprop net.dns1").exec().out.firstOrNull()?.ifBlank { null }
        val dns2 = Shell.cmd("getprop net.dns2").exec().out.firstOrNull()?.ifBlank { null }
        val privateMode = Shell.cmd("settings get global private_dns_mode").exec()
            .out.firstOrNull()?.takeIf { it != "null" && it.isNotBlank() }
        val privateSpecifier = Shell.cmd("settings get global private_dns_specifier").exec()
            .out.firstOrNull()?.takeIf { it != "null" && it.isNotBlank() }
        return mapOf(
            "dns1" to dns1,
            "dns2" to dns2,
            "privateDnsMode" to privateMode,
            "privateDnsSpecifier" to privateSpecifier
        )
    }

    fun setDns(dns1: String, dns2: String? = null) {
        Shell.cmd("setprop net.dns1 '${sanitizeShellArg(dns1)}'").exec()
        if (dns2 != null) {
            Shell.cmd("setprop net.dns2 '${sanitizeShellArg(dns2)}'").exec()
        }
    }

    fun clearDns() {
        Shell.cmd("setprop net.dns1 ''").exec()
        Shell.cmd("setprop net.dns2 ''").exec()
    }

    fun setPrivateDns(hostname: String) {
        Shell.cmd("settings put global private_dns_mode hostname").exec()
        Shell.cmd("settings put global private_dns_specifier '${sanitizeShellArg(hostname)}'").exec()
    }

    fun clearPrivateDns() {
        Shell.cmd("settings put global private_dns_mode opportunistic").exec()
        Shell.cmd("settings delete global private_dns_specifier").exec()
    }

    fun getProcessInfo(pid: Int): Map<String, Any?> {
        val status = Shell.cmd("cat /proc/$pid/status").exec()
        val cmdline = Shell.cmd("cat /proc/$pid/cmdline").exec()

        val smap = if (status.isSuccess) {
            status.out.mapNotNull {
                val i = it.indexOf(":")
                if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
            }.toMap()
        } else emptyMap()

        return mapOf(
            "pid" to pid,
            "name" to smap["Name"],
            "state" to smap["State"],
            "ppid" to smap["PPid"]?.toIntOrNull(),
            "uid" to smap["Uid"],
            "gid" to smap["Gid"],
            "vmPeak" to smap["VmPeak"],
            "vmSize" to smap["VmSize"],
            "vmRSS" to smap["VmRSS"],
            "cmdline" to if (cmdline.isSuccess) cmdline.out.joinToString("")
                .ifBlank { null } else null
        )
    }

}