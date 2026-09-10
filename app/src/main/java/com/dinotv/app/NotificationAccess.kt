package com.dinotv.app

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.os.SystemClock

/**
 * MediaSession titles require the notification listener grant.
 * Reinstalling the APK clears it — restore the same way we bounce accessibility.
 */
object NotificationAccess {
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    private const val REBIND_EVERY_MS = 15_000L

    @Volatile
    private var lastRebindAt = 0L

    fun ensureEnabled(context: Context) {
        try {
            val resolver = context.contentResolver
            val component = ComponentName(context, NowPlayingListener::class.java)
            val expected = component.flattenToString()
            val current = Settings.Secure.getString(resolver, ENABLED_NOTIFICATION_LISTENERS).orEmpty()
            if (!current.split(':').any { it.equals(expected, ignoreCase = true) }) {
                val next = if (current.isBlank()) expected else "$current:$expected"
                Settings.Secure.putString(resolver, ENABLED_NOTIFICATION_LISTENERS, next)
            }
            if (!NowPlayingListener.connected()) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastRebindAt >= REBIND_EVERY_MS) {
                    lastRebindAt = now
                    if (Build.VERSION.SDK_INT >= 24) {
                        NotificationListenerService.requestRebind(component)
                    }
                }
            }
        } catch (_: SecurityException) {
            // Needs WRITE_SECURE_SETTINGS once over adb (same grant as accessibility).
        } catch (_: Exception) {
            // Next poll can retry.
        }
    }

    fun enabled(context: Context): Boolean {
        val expected = ComponentName(context, NowPlayingListener::class.java).flattenToString()
        val current = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS,
        ).orEmpty()
        return current.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
