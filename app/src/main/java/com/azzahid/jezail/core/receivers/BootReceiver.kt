package com.azzahid.jezail.core.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.core.services.HttpServerService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            HttpServerService.start(context, Preferences.serverPort)
        }
    }
}
