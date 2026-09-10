package com.dinotv.app

import android.content.Context

object TvPrefs {
    fun remoteEnabled(context: Context): Boolean = prefs(context).getBoolean("remoteEnabled", false)

    fun saveRemoteEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("remoteEnabled", enabled).apply()
    }
    private const val PREFS = "dino_tv"
    private const val SESSION = "session"
    private const val POWER_AT = "powerAt"
    private const val CUES = "shownCues"
    private const val OVERLAY_PROMPTED = "overlayPrompted"
    private const val LISTENER_PROMPTED = "listenerPrompted"
    private const val MUSIC_COMMAND_AT = "musicCommandAt"
    private const val TV_COMMAND_AT = "tvCommandAt"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun session(context: Context): String = prefs(context).getString(SESSION, "").orEmpty()

    fun saveSession(context: Context, token: String) {
        prefs(context).edit().putString(SESSION, token).apply()
    }

    fun powerAt(context: Context): String = prefs(context).getString(POWER_AT, "").orEmpty()

    fun savePowerAt(context: Context, token: String) {
        prefs(context).edit().putString(POWER_AT, token).apply()
    }

    fun shownCues(context: Context): Set<String> =
        prefs(context).getString(CUES, "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun markCueShown(context: Context, id: String): Boolean {
        val shown = shownCues(context)
        if (id in shown) return false
        val next = (shown + id).toList().takeLast(40)
        prefs(context).edit().putString(CUES, next.joinToString("\n")).apply()
        return true
    }

    fun overlayPrompted(context: Context): Boolean = prefs(context).getBoolean(OVERLAY_PROMPTED, false)

    fun markOverlayPrompted(context: Context) {
        prefs(context).edit().putBoolean(OVERLAY_PROMPTED, true).apply()
    }

    fun listenerPrompted(context: Context): Boolean = prefs(context).getBoolean(LISTENER_PROMPTED, false)

    fun markListenerPrompted(context: Context) {
        prefs(context).edit().putBoolean(LISTENER_PROMPTED, true).apply()
    }

    fun musicCommandAt(context: Context): String = prefs(context).getString(MUSIC_COMMAND_AT, "").orEmpty()

    fun saveMusicCommandAt(context: Context, token: String) {
        prefs(context).edit().putString(MUSIC_COMMAND_AT, token).apply()
    }

    fun tvCommandAt(context: Context): String = prefs(context).getString(TV_COMMAND_AT, "").orEmpty()

    fun saveTvCommandAt(context: Context, token: String) {
        prefs(context).edit().putString(TV_COMMAND_AT, token).apply()
    }
}
