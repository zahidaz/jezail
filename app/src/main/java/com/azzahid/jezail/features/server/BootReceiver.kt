package com.azzahid.jezail.features.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i(TAG, "Boot completed, auto-starting HTTP server service")

            val serviceIntent = Intent(context, HttpServerService::class.java).apply {
                action = HttpServerService.ACTION_START_SERVER
                putExtra(HttpServerService.EXTRA_PORT, HttpServerService.DEFAULT_PORT)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "Service start request sent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service on boot", e)
            }
        }
    }
}