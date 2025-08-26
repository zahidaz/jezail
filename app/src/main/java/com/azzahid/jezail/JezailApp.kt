package com.azzahid.jezail

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.azzahid.jezail.features.RootFSService
import com.topjohnwu.superuser.Shell

class JezailApp : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        Shell.enableVerboseLogging =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Shell.setDefaultBuilder(
            Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10)
        )

        Shell.getShell { shell ->
            if (shell.isRoot) {
                Log.d("Root", "Root access granted")
                RootFSService.bind(this@JezailApp)
            } else {
                Log.e("Root", "Root access denied")
            }
        }
    }
}
