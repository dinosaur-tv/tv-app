package com.dinotv.app

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

class NowPlayingListener : NotificationListenerService() {
    private val musicCache = ConcurrentHashMap<String, StatusBarNotification>()

    override fun onListenerConnected() {
        instance = this
        musicCache.clear()
        try {
            for (notification in activeNotifications) {
                remember(notification)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "activeNotifications on connect failed", error)
        }
        Log.i(TAG, "connected cache=${musicCache.size}")
        ContextCompat.startForegroundService(this, Intent(this, TvWakeService::class.java))
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        Log.i(TAG, "disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        remember(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        musicCache.remove(sbn.key)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun remember(sbn: StatusBarNotification) {
        if (isMusicPackage(sbn.packageName)) {
            musicCache[sbn.key] = sbn
        } else {
            musicCache.remove(sbn.key)
        }
    }

    companion object {
        private const val TAG = "NowPlaying"

        @Volatile
        private var instance: NowPlayingListener? = null

        fun connected(): Boolean = instance != null

        fun activeNotifications(): Array<StatusBarNotification>? {
            val listener = instance ?: return null
            return try {
                val live = listener.activeNotifications
                if (!live.isNullOrEmpty()) {
                    val liveKeys = live.asSequence().map { it.key }.toHashSet()
                    listener.musicCache.keys.removeIf { key -> key !in liveKeys }
                    for (notification in live) listener.remember(notification)
                    live
                } else {
                    listener.musicCache.values.toTypedArray()
                }
            } catch (error: Throwable) {
                Log.w(TAG, "activeNotifications failed", error)
                listener.musicCache.values.toTypedArray().ifEmpty { null }
            }
        }

        fun isMusicPackage(packageName: String): Boolean {
            val name = packageName.lowercase()
            return name.contains("kinopoisk") || name.contains("yandex") || name.contains("music")
        }
    }
}
