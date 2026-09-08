package com.dinotv.app

import android.content.Context

object TvPrefs {
    private const val PREFS = "dino_tv"
    private const val SESSION = "session"
    private const val POWER_AT = "powerAt"
    private const val CUES = "shownCues"
    private const val OVERLAY_PROMPTED = "overlayPrompted"

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
}
