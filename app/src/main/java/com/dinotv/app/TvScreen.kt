package com.dinotv.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.net.Uri

object TvScreen {
    const val HOME = BuildConfig.HOME_URL

    fun url(session: String): String =
        if (session.isBlank()) HOME else "$HOME#$session"

    @SuppressLint("SetJavaScriptEnabled")
    fun bind(webView: WebView, bridge: DinoTvBridge, session: String) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val home = Uri.parse(HOME)
                val target = request.url
                return target.scheme != "https" || target.host != home.host ||
                    target.port != home.port || target.userInfo != null
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                TvAudio.abandon(webView.context)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.addJavascriptInterface(bridge, "DinoTV")
        webView.loadUrl(url(session))
    }
}
