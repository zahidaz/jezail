package com.azzahid.jezail.ui.viewmodels

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azzahid.jezail.core.data.models.ServerStatus
import com.azzahid.jezail.core.data.models.ServerUiState
import com.azzahid.jezail.core.services.HttpServerService
import com.azzahid.jezail.features.managers.AdbManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ServerUiState(
        serverStatus = HttpServerService.defaultServerStatus,
        isRooted = false,
        isAdbRunning = false,
        adbVersion = "Unknown"
    ))
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())

    init {
        viewModelScope.launch {
            Log.i(TAG, "ViewModel initializing")
            val isRooted = runCatching { Shell.getShell().isRoot }.getOrElse {
                Log.e(TAG, "Error checking root status", it); false
            }
            _uiState.value = _uiState.value.copy(isRooted = isRooted)
            if (isRooted) updateAdbStatus()
        }
    }


    fun startServer(context: Context) {
        if (!_uiState.value.isRooted) {
            Log.w(TAG, "Cannot start server - device is not rooted")
            return
        }
        Log.d(TAG, "Starting server on port ${_uiState.value.serverStatus.port}")
        HttpServerService.start(context, _uiState.value.serverStatus.port)
    }

    fun stopServer(context: Context) {
        HttpServerService.stop(context)
    }

    fun setServerPort(port: Int) {
        updateServerStatus(_uiState.value.serverStatus.copy(port = port))
    }

    fun updateServerStatus(serverStatus: ServerStatus?) {
        serverStatus?.let {
            _uiState.value = _uiState.value.copy(serverStatus = it)
        } ?: {
            _uiState.value = _uiState.value.copy(HttpServerService.defaultServerStatus)
        }
    }

    fun startAdb() = executeAdbCommand("start") { AdbManager.start() }
    fun stopAdb() = executeAdbCommand("stop") { AdbManager.stop() }

    private fun executeAdbCommand(action: String, command: () -> Unit) {
        if (!_uiState.value.isRooted) {
            Log.w(TAG, "Cannot $action ADB - device is not rooted")
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "Requesting ADB $action")
            command()
            Log.d(TAG, "ADB $action result: ${AdbManager.getStatus()}")
            handler.postDelayed({ updateAdbStatus() }, 500)
        }
    }

    private fun updateAdbStatus() {
        if (!_uiState.value.isRooted) {
            _uiState.value = _uiState.value.copy(isAdbRunning = false, adbVersion = "Root required")
            return
        }
        runCatching {
            val adbStatus = AdbManager.getStatus()
            Log.d(TAG, "ADB status updated: $adbStatus")
            _uiState.value = _uiState.value.copy(
                isAdbRunning = adbStatus["isRunning"] as Boolean,
            )
        }.onFailure { e ->
            Log.e(TAG, "Error updating ADB status", e)
            _uiState.value = _uiState.value.copy(isAdbRunning = false, adbVersion = "Error")
        }
    }

    fun refreshAdbStatus() {
        if (_uiState.value.isRooted) updateAdbStatus()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}