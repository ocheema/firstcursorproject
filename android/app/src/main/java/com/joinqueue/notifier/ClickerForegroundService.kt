package com.joinqueue.notifier

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/** Keeps the process alive while the in-app WebView clicker is running. */
class ClickerForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannel(this)
        val notification = Notifier.foregroundNotification(this, "Clicking Join Queue in-app…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                Notifier.FG_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifier.FG_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.setRunning(this, false)
        super.onDestroy()
    }
}
