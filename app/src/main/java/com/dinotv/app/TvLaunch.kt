package com.dinotv.app

import android.content.Context
import android.content.Intent

object TvLaunch {
    const val FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_CLEAR_TOP

    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            addFlags(FLAGS)
        }
}
