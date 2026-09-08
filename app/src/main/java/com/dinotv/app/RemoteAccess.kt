package com.dinotv.app

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings

object RemoteAccess {
    private const val SERVICE = "com.dinotv.app/.RemoteAccessibilityService"
    private const val BOUNCE_EVERY_MS = 8_000L

    @Volatile
    private var lastBounceAt = 0L

    fun ensureEnabled(context: Context) {
        // Settings can list us as enabled while the service process is dead.
        // Only skip work when the live instance is actually bound.
        if (RemoteAccessibilityService.connected()) return
        try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val parts = current.split(':').filter { it.isNotBlank() }.toMutableList()
            val already = parts.any { it.equals(SERVICE, ignoreCase = true) }
            val now = SystemClock.elapsedRealtime()
            if (already && now - lastBounceAt >= BOUNCE_EVERY_MS) {
                lastBounceAt = now
                // Bounce the service so Android rebinds a crashed/dead instance.
                parts.removeAll { it.equals(SERVICE, ignoreCase = true) }
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    parts.joinToString(":"),
                )
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (parts.isEmpty()) 0 else 1)
            }
            val refreshed = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val enabledParts = refreshed.split(':').filter { it.isNotBlank() }
            if (enabledParts.any { it.equals(SERVICE, ignoreCase = true) }) return
            val next = if (refreshed.isBlank()) SERVICE else "$refreshed:$SERVICE"
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
