package com.dinotv.app

import android.content.Context
import android.content.Intent

object TvApps {
    const val KINOPOISK = "ru.kinopoisk.tv"

    fun open(context: Context, app: String): Boolean = when (app) {
        "kinopoisk" -> openPackage(context, KINOPOISK)
        "dino" -> {
            context.startActivity(TvLaunch.intent(context))
            true
        }
        else -> false
    }

    private fun openPackage(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val leanback = pm.getLeanbackLaunchIntentForPackage(packageName)
        val launcher = leanback ?: pm.getLaunchIntentForPackage(packageName)
        if (launcher == null) return false
        launcher.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        return try {
            context.startActivity(launcher)
            true
        } catch (_: Exception) {
            false
        }
    }
}
