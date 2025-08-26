package com.azzahid.jezail.core.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

suspend fun <T> withRootFS(block: suspend (FileSystemManager) -> T): T {
    val binder = RootFSService.getBinder()
    return block(FileSystemManager.getRemote(binder))
}

suspend fun <T> withRootFSFile(path: String, block: suspend (ExtendedFile) -> T): T {
    val binder = RootFSService.getBinder()
    return block(FileSystemManager.getRemote(binder).getFile(path))
}

object RootFSService {
    private const val TAG = "RootFSService"

    class Service : RootService() {
        override fun onBind(intent: Intent): IBinder = FileSystemManager.getService()
    }

    @Volatile
    private var binder: IBinder? = null
    private val connected = MutableStateFlow(false)
    val isConnected = connected.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
            connected.value = false
            Log.w(TAG, "Service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            binder = null
            connected.value = false
            Log.e(TAG, "Service binding died")
        }
    }

    fun getBinder(): IBinder {
        return binder?.takeIf { it.isBinderAlive } ?: run {
            Log.e(TAG, "Service not connected")
            throw IllegalStateException("Service not connected")
        }
    }

    fun bind(context: Context) {
        if (connected.value) return
        val intent = Intent(context, Service::class.java)
        RootService.bind(intent, serviceConnection)
    }

    fun unbind() {
        if (connected.value) {
            try {
                RootService.unbind(serviceConnection)
            } finally {
                binder = null
                connected.value = false
            }
        }
    }
}