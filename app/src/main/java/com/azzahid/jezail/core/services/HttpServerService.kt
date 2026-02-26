package com.azzahid.jezail.core.services

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.azzahid.jezail.MainActivity
import com.azzahid.jezail.core.api.buildServer
import com.azzahid.jezail.core.data.models.ServerStatus
import com.azzahid.jezail.core.utils.getLocalIpAddress

class HttpServerService : Service() {

    companion object {
        private const val TAG = "HttpServerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "http_server_channel"
        private const val ACTION_START_SERVER = "start_server"
        private const val ACTION_STOP_SERVER = "stop_server"
        private const val EXTRA_PORT = "port"
        private const val DEFAULT_PORT = 8080

        val defaultServerStatus = ServerStatus(
            isRunning = false, port = DEFAULT_PORT, ipAddress = "Unknown", url = null
        )

        private fun startService(context: Context, action: String, port: Int) {
            val intent = Intent(context, HttpServerService::class.java).apply {
                this.action = action
                putExtra(EXTRA_PORT, port)
            }
            try {
                if (SDK_INT >= O) context.startForegroundService(intent)
                else context.startService(intent)
                Log.d(TAG, "Requested to $action")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to $action service", e)
            }
        }

        fun start(context: Context, port: Int = DEFAULT_PORT) =
            startService(context, ACTION_START_SERVER, port)

        fun stop(context: Context) = startService(context, ACTION_STOP_SERVER, DEFAULT_PORT)


    }

    private var port = DEFAULT_PORT
    private var httpServer = buildServer(port)
    private var isServerRunning = false
    private val localIp: String get() = getLocalIpAddress()
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
            ACTION_START_SERVER -> startHttpServer(intent.getIntExtra(EXTRA_PORT, port))
            ACTION_STOP_SERVER -> stopHttpServer()
        }
        return START_STICKY
    }

    private fun startHttpServer(port: Int) {
        this.port = port
        httpServer.stop()
        try {
            httpServer = buildServer(port).apply { start() }
            isServerRunning = true
            startForeground(NOTIFICATION_ID, createNotification(port))
            Log.i(TAG, "Server started on port $port")
        } catch (e: Exception) {
            isServerRunning = false
            Log.e(TAG, "Failed to start server on port $port", e)
            startForeground(NOTIFICATION_ID, createNotification(port))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        if (SDK_INT >= O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "HTTP Server Service", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jezail HTTP Server Status"
                setSound(null, null)
                enableVibration(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(port: Int): Notification {
        val statusText = if (isServerRunning) "$localIp:$port" else "Stopped"

        fun pendingServiceIntent(action: String, requestCode: Int) = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, HttpServerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Jezail Server")
            .setContentText(statusText).setSmallIcon(R.drawable.ic_dialog_info).setColor(
                ContextCompat.getColor(
                    this, if (isServerRunning) R.color.holo_green_dark else R.color.holo_red_dark
                )
            ).setContentIntent(mainPendingIntent).setOngoing(true).addAction(
                R.drawable.ic_media_pause, "Stop", pendingServiceIntent(ACTION_STOP_SERVER, 1)
            ).addAction(
                R.drawable.ic_popup_sync, "Restart", pendingServiceIntent(ACTION_START_SERVER, 2)
            ).setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Server Status: $statusText\nTap to open app, use buttons to control server")
            ).build()
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIFICATION_ID, createNotification(port)
        )
    }


    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        httpServer.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        super.onDestroy()
    }

    fun getServerStatus() = ServerStatus(
        isRunning = isServerRunning,
        port = port,
        ipAddress = localIp,
        url = if (isServerRunning) "http://$localIp:$port" else null
    )

}