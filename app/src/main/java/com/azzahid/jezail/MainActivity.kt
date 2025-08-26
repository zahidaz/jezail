package com.azzahid.jezail

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azzahid.jezail.features.managers.AdbManager
import com.azzahid.jezail.core.services.HttpServerService
import com.azzahid.jezail.ui.screens.MainScreen
import com.azzahid.jezail.ui.theme.AppTheme
import com.topjohnwu.superuser.Shell

class MainActivity : ComponentActivity() {
    private var httpServerService: HttpServerService? = null
    private var isServiceBound = false

    private var isServerRunning by mutableStateOf(false)
    private var deviceIpAddress by mutableStateOf("Unknown")
    private var serverPort by mutableIntStateOf(8080)
    private var isRooted by mutableStateOf(false)
    private var isAdbRunning by mutableStateOf(false)
    private var adbVersion by mutableStateOf("unknown")

    private val handler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            httpServerService = (service as HttpServerService.HttpServerBinder).getService()
            isServiceBound = true
            updateServerStatus()
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            httpServerService = null
            isServiceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.i(TAG, "Application starting")

        isRooted = try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            Log.e(TAG, "Error checking root status", e)
            false
        }

        setContent {
            AppTheme {
                MainScreen(
                    isServerRunning = isServerRunning,
                    deviceIpAddress = deviceIpAddress,
                    serverPort = serverPort,
                    isRooted = isRooted,
                    isAdbRunning = isAdbRunning,
                    onStartServer = { startServer() },
                    onStopServer = { stopServer() },
                    onStartAdb = { startAdb() },
                    onStopAdb = { stopAdb() },
                    onOpenPermissions = { },
                    onPortChange = { newPort -> changeServerPort(newPort) }
                )
            }
        }

        bindToService()

        if (isRooted) {
            updateAdbStatus()
            startServer()
        }
    }

    private fun bindToService() {
        Intent(this, HttpServerService::class.java).also {
            bindService(it, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startServer() {
        if (!isRooted) {
            Log.w(TAG, "Cannot start server - device is not rooted")
            return
        }
        val intent = Intent(this, HttpServerService::class.java).apply {
            action = HttpServerService.ACTION_START_SERVER
            putExtra(HttpServerService.EXTRA_PORT, serverPort)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "Requested to start server")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server service", e)
        }
        delayedStatusUpdate()
    }

    private fun stopServer() {
        if (!isRooted) {
            Log.w(TAG, "Cannot stop server - device is not rooted")
            return
        }
        Intent(this, HttpServerService::class.java).apply {
            action = HttpServerService.ACTION_STOP_SERVER
        }.also {
            startService(it)
        }
        delayedStatusUpdate()
    }

    private fun updateServerStatus() {
        Log.d(TAG, "Updating server status; serviceBound=$isServiceBound")
        val status = httpServerService?.getServerStatus()
        if (status != null) {
            isServerRunning = status.isRunning
            deviceIpAddress = status.ipAddress
            serverPort = status.port
            Log.d(
                TAG,
                "Server status updated: running=$isServerRunning, ip=$deviceIpAddress, port=$serverPort"
            )
        } else {
            Log.w(TAG, "HttpServerService is null - cannot update status")
            isServerRunning = false
            deviceIpAddress = "Unknown"
        }
    }

    private fun startAdb() {
        if (!isRooted) {
            Log.w(TAG, "Cannot start ADB - device is not rooted")
            return
        }
        Log.d(TAG, "Requesting ADB start")
        AdbManager.start()
        Log.d(TAG, "ADB start result: ${AdbManager.getStatus()}")
        delayedStatusUpdate()
    }

    private fun stopAdb() {
        if (!isRooted) {
            Log.w(TAG, "Cannot stop ADB - device is not rooted")
            return
        }
        Log.d(TAG, "Requesting ADB stop")
        AdbManager.stop()
        Log.d(TAG, "ADB stop result: ${AdbManager.getStatus()}")
        delayedStatusUpdate()
    }

    private fun updateAdbStatus() {
        if (!isRooted) {
            isAdbRunning = false
            adbVersion = "Root required"
            return
        }
        try {
            Log.d(TAG, "ADB status updated: ${AdbManager.getStatus()} ")
            isAdbRunning = AdbManager.getStatus()["isRunning"] == "true"
            adbVersion = AdbManager.getStatus()["version"] ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error updating ADB status", e)
            isAdbRunning = false
            adbVersion = "Error"
        }
    }

    private fun changeServerPort(newPort: Int) {
        if (isServerRunning) {
            Log.w(TAG, "Cannot change port while server is running")
            return
        }
        Log.d(TAG, "Changing server port from $serverPort to $newPort")
        serverPort = newPort
    }

    private fun delayedStatusUpdate(delayMillis: Long = 500) {
        handler.postDelayed(::updateServerStatus, delayMillis)
    }

    override fun onResume() {
        super.onResume()
        if (isServiceBound) updateServerStatus()
        if (isRooted) updateAdbStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        Log.i(TAG, "Application shutting down")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
