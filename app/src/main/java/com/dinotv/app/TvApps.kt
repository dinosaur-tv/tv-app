package com.dinotv.app

import android.content.Context
import android.content.Intent

object TvApps {
    const val KINOPOISK = "ru.kinopoisk.tv"

    fun intent(context: Context, app: String): Intent? = when (app) {
        "kinopoisk" -> packageIntent(context, KINOPOISK)
        "dino" -> TvLaunch.intent(context)
        else -> null
    }

    fun open(context: Context, app: String): Boolean {
        val launch = intent(context, app) ?: return false
        return try {
            context.startActivity(launch)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun packageIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        val leanback = pm.getLeanbackLaunchIntentForPackage(packageName)
        val launcher = leanback ?: pm.getLaunchIntentForPackage(packageName)
        val resolved = launcher ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(packageName)
            val match = pm.queryIntentActivities(this, 0).firstOrNull() ?: return null
            setClassName(match.activityInfo.packageName, match.activityInfo.name)
        }
        return resolved.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
    }
}
