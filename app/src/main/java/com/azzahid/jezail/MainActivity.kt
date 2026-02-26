package com.azzahid.jezail

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.azzahid.jezail.core.services.HttpServerService
import com.azzahid.jezail.ui.screens.MainScreen
import com.azzahid.jezail.ui.theme.AppTheme
import com.azzahid.jezail.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var httpServerService: HttpServerService? = null
    private var isServiceBound = false
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
            viewModel.updateServerStatus(null)
            Log.d(TAG, "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Activity starting")

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            AppTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isRooted) {
                    if (uiState.isRooted) {
                        viewModel.startServer(this@MainActivity)
                        delayedStatusUpdate()
                    }
                }

                MainScreen(
                    isServerRunning = uiState.serverStatus.isRunning,
                    deviceIpAddress = uiState.serverStatus.ipAddress,
                    serverPort = uiState.serverStatus.port,
                    isRooted = uiState.isRooted,
                    isAdbRunning = uiState.isAdbRunning,
                    isAuthEnabled = uiState.isAuthEnabled,
                    authPin = uiState.authPin,
                    onStartServer = {
                        viewModel.startServer(this)
                        delayedStatusUpdate()
                    },
                    onStopServer = {
                        viewModel.stopServer(this)
                        delayedStatusUpdate()
                    },
                    onStartAdb = {
                        viewModel.startAdb()
                        delayedStatusUpdate()
                    },
                    onStopAdb = {
                        viewModel.stopAdb()
                    },
                    onToggleAuth = { enabled -> viewModel.toggleAuth(enabled) },
                    onRegeneratePin = { viewModel.regeneratePin() },
                    onPortChange = { newPort -> viewModel.setServerPort(newPort) },
                    onAdbPortChange = { newPort -> viewModel.setAdbPort(newPort) })
            }
        }

        bindToService()
    }

    private fun bindToService() {
        Intent(this, HttpServerService::class.java).also {
            bindService(it, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun updateServerStatus() {
        Log.d(TAG, "Updating server status; serviceBound=$isServiceBound")
        viewModel.updateServerStatus(httpServerService?.getServerStatus())
    }

    private fun delayedStatusUpdate(delayMillis: Long = 500) {
        handler.postDelayed(::updateServerStatus, delayMillis)
    }

    override fun onResume() {
        super.onResume()
        if (isServiceBound) updateServerStatus()
        viewModel.refreshAdbStatus()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onDestroy()
        Log.i(TAG, "Activity shutting down")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}