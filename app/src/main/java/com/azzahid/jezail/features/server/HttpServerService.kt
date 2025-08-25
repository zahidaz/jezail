package com.azzahid.jezail.features.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.azzahid.jezail.MainActivity
import com.azzahid.jezail.core.api.buildServer
import com.azzahid.jezail.core.api.getLocalIpAddress

class HttpServerService : Service() {

    companion object {
        private const val TAG = "HttpServerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "http_server_channel"
        const val ACTION_START_SERVER = "start_server"
        const val ACTION_STOP_SERVER = "stop_server"
        const val EXTRA_PORT = "port"
        const val DEFAULT_PORT = 8080
    }

    private var httpServer = buildServer(DEFAULT_PORT)
    private var isServerRunning = false
    private val localIp = getLocalIpAddress()
    private val binder = HttpServerBinder()

    inner class HttpServerBinder : Binder() {
        fun getService() = this@HttpServerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startHttpServer(intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT))
            ACTION_STOP_SERVER -> stopHttpServer()
            else -> startHttpServer(DEFAULT_PORT)
        }
        return START_STICKY
    }

    private fun startHttpServer(port: Int) {
        httpServer.stop()
        httpServer = buildServer(port).apply { start() }
        isServerRunning = true
        startForeground(NOTIFICATION_ID, createNotification(port))
        Log.i(TAG, "Server started on port $port")
    }

    private fun stopHttpServer() {
        try {
            httpServer.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
            isServerRunning = false
            Log.i(TAG, "Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HTTP Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jezail HTTP Server Status"
                setSound(null, null)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(port: Int): Notification {
        val statusText = if (isServerRunning) "$localIp:$port" else "Stopped"

        fun pendingServiceIntent(action: String, requestCode: Int) =
            PendingIntent.getService(
                this,
                requestCode,
                Intent(this, HttpServerService::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jezail Server")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(
                ContextCompat.getColor(
                    this,
                    if (isServerRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                )
            )
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                pendingServiceIntent(ACTION_STOP_SERVER, 1)
            )
            .addAction(
                android.R.drawable.ic_popup_sync,
                "Restart",
                pendingServiceIntent(ACTION_START_SERVER, 2)
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Server Status: $statusText\nTap to open app, use buttons to control server")
            )
            .build()
    }

    private fun updateNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, createNotification(DEFAULT_PORT))
    }


    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        httpServer.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        super.onDestroy()
    }

    fun getServerStatus() = ServerStatus(
        isRunning = isServerRunning,
        port = DEFAULT_PORT,
        ipAddress = localIp,
        url = if (isServerRunning) "http://$localIp:$DEFAULT_PORT" else null
    )

    data class ServerStatus(
        val isRunning: Boolean,
        val port: Int,
        val ipAddress: String,
        val url: String?
    )
}
