package com.dinotv.app

import android.webkit.JavascriptInterface

class DinoTvBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun saveSession(token: String) {
        if (token.isBlank()) return
        TvPrefs.saveSession(activity, token)
        activity.startWakeService()
    }

    @JavascriptInterface
    fun powerOff() {
        activity.runOnUiThread { activity.finish() }
    }
}
