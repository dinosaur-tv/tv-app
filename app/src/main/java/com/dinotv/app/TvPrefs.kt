package com.dinotv.app

import android.content.Context

object TvPrefs {
    private const val PREFS = "dino_tv"
    private const val SESSION = "session"
    private const val POWER_AT = "powerAt"

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
}
