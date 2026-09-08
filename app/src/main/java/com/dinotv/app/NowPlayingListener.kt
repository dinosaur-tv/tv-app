package com.dinotv.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat

class NowPlayingListener : NotificationListenerService() {
    override fun onListenerConnected() {
        ContextCompat.startForegroundService(this, Intent(this, TvWakeService::class.java))
    }
}
