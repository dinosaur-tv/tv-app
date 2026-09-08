package com.dinotv.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * MediaSession titles require the notification listener grant.
 * Reinstalling the APK clears it — restore the same way we bounce accessibility.
 */
object NotificationAccess {
    fun ensureEnabled(context: Context) {
        try {
            val resolver = context.contentResolver
            val expected = ComponentName(context, NowPlayingListener::class.java).flattenToString()
            val current = Settings.Secure.getString(resolver, ENABLED_NOTIFICATION_LISTENERS).orEmpty()
            val parts = current.split(':').filter { it.isNotBlank() }
            if (parts.any { it.equals(expected, ignoreCase = true) }) return
            val next = if (current.isBlank()) expected else "$current:$expected"
            Settings.Secure.putString(resolver, ENABLED_NOTIFICATION_LISTENERS, next)
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

    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
}
