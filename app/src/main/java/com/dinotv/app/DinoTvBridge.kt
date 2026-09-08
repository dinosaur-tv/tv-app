package com.dinotv.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat

class DinoTvBridge(
    private val context: Context,
    private val onPowerOff: () -> Unit,
) {
    @JavascriptInterface
    fun saveSession(token: String) {
        if (token.isBlank()) return
        TvPrefs.saveSession(context, token)
        ContextCompat.startForegroundService(context, Intent(context, TvWakeService::class.java))
    }

    @JavascriptInterface
    fun nowPlaying(): String = NowPlayingDesk.json(context)

    @JavascriptInterface
    fun isForeground(): String = if (TvForeground.visible) "1" else "0"

    @JavascriptInterface
    fun powerOff() {
        Handler(Looper.getMainLooper()).post(onPowerOff)
    }
}
