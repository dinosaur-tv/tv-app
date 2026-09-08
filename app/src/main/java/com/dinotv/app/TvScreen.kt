package com.dinotv.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object TvScreen {
    const val HOME = "https://home.dym-dino.ru/tv/"

    fun url(session: String): String =
        if (session.isBlank()) HOME else "$HOME#$session"

    @SuppressLint("SetJavaScriptEnabled")
    fun bind(webView: WebView, bridge: DinoTvBridge, session: String) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                TvAudio.abandon(webView.context)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.addJavascriptInterface(bridge, "DinoTV")
        webView.loadUrl(url(session))
    }
}
