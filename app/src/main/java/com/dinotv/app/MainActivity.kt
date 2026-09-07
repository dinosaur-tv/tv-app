package com.dinotv.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startWakeService()
        val screen = WebView(this)
        screen.webViewClient = WebViewClient()
        screen.webChromeClient = WebChromeClient()
        screen.settings.javaScriptEnabled = true
        screen.settings.domStorageEnabled = true
        screen.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        screen.settings.mediaPlaybackRequiresUserGesture = false
        screen.addJavascriptInterface(DinoTvBridge(this), "DinoTV")
        screen.loadUrl("https://home.dym-dino.ru/tv/")
        setContentView(screen)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    fun startWakeService() {
        ContextCompat.startForegroundService(this, Intent(this, TvWakeService::class.java))
    }
}
