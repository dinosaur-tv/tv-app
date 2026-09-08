package com.dinotv.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object RemoteAccess {
    private const val SERVICE = "com.dinotv.app/.RemoteAccessibilityService"

    fun ensureEnabled(context: Context) {
        if (connected(context)) return
        try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val next = if (current.split(':').any { it.equals(SERVICE, ignoreCase = true) }) {
                current
            } else if (current.isBlank()) {
                SERVICE
            } else {
                "$current:$SERVICE"
            }
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: SecurityException) {
            // Needs WRITE_SECURE_SETTINGS granted once over adb.
        } catch (_: Exception) {
            // Quiet: the next poll can try again.
        }
    }

    fun connected(context: Context): Boolean {
        if (RemoteAccessibilityService.connected()) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = ComponentName(context, RemoteAccessibilityService::class.java)
        return enabled.split(':').any { component ->
            ComponentName.unflattenFromString(component)?.equals(expected) == true
        }
    }
}
