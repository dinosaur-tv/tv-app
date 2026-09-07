package com.dinotv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val screen = WebView(this)
        screen.webViewClient = WebViewClient()
        screen.webChromeClient = WebChromeClient()
        screen.settings.javaScriptEnabled = true
        screen.settings.domStorageEnabled = true
        screen.settings.cacheMode = WebSettings.LOAD_DEFAULT
        screen.settings.mediaPlaybackRequiresUserGesture = false
        screen.loadUrl("https://home.dym-dino.ru/tv/")
        setContentView(screen)
    }
}
