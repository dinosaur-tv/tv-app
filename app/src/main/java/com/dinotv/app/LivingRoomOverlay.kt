package com.dinotv.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebView

object LivingRoomOverlay {
    private var webView: WebView? = null
    // An opaque overlay covers the player's keep-awake window, so it needs its own flag.
    internal const val WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    val visible: Boolean get() = webView != null

    fun show(context: Context) {
        if (webView != null) return
        if (!Settings.canDrawOverlays(context)) return
        val app = context.applicationContext
        val screen = WebView(app)
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WINDOW_FLAGS,
            PixelFormat.OPAQUE,
        )
        TvScreen.bind(screen, DinoTvBridge(app) { hide() }, TvPrefs.session(app))
        TvAudio.abandon(app)
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.addView(screen, params)
            webView = screen
            TvForeground.visible = true
        } catch (_: Exception) {
            screen.destroy()
            return
        }
        OverlayScreenAwake.start(app)
    }

    fun hide() {
        OverlayScreenAwake.stop()
        val screen = webView ?: return
        webView = null
        TvForeground.visible = false
        try {
            (screen.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(screen)
        } catch (_: Exception) {
            // Already gone.
        }
        screen.destroy()
    }
}
