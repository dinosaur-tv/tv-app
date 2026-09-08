package com.dinotv.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object TvApps {
    const val KINOPOISK = "ru.kinopoisk.tv"

    private val kinopoiskLaunchers = listOf(
        "ru.kinopoisk.tv.presentation.splash.SplashActivity",
        "ru.kinopoisk.tv.splash.impl.presentation.SplashScreenActivity",
        "ru.kinopoisk.tv.hd.presentation.home.HomeActivity",
    )

    fun intent(context: Context, app: String): Intent? = when (app) {
        "kinopoisk" -> packageIntent(context, KINOPOISK) ?: explicitKinopoisk(context)
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
        if (launcher != null) {
            return launcher.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        }
        val probe = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(packageName)
        }
        val match = try {
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            component = ComponentName(match.activityInfo.packageName, match.activityInfo.name)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        }
    }

    private fun explicitKinopoisk(context: Context): Intent? {
        // Even without package visibility, an explicit component can still be started.
        for (className in kinopoiskLaunchers) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                component = ComponentName(KINOPOISK, className)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
            }
        }
        return null
    }
}
