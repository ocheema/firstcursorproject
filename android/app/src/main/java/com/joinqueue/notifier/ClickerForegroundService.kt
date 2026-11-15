package com.joinqueue.notifier

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

class ClickerForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannel(this)
        val notification = Notifier.foregroundNotification(this)
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
        JoinQueueAccessibilityService.instance?.startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        JoinQueueAccessibilityService.instance?.stopLoop()
        Prefs.setRunning(this, false)
        super.onDestroy()
    }
}
