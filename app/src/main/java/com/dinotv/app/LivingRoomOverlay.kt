package com.dinotv.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.content.ContextCompat

object LivingRoomOverlay {
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    // Focusable so Back / Home on the physical remote can dismiss «Поверх музыки»
    // instead of silently driving the player underneath a frozen Dino picture.
    internal const val WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    val visible: Boolean get() = webView != null

    fun show(context: Context) {
        if (webView != null) return
        if (!Settings.canDrawOverlays(context)) return
        if (TvPrefs.userDismissed(context)) return
        val app = context.applicationContext
        // A full-screen APPLICATION_OVERLAY hides the player from accessibility.
        // Use the connected service's window context/token so track changes remain
        // readable underneath Dino, including on TVs with no MediaSession.
        val accessibility = RemoteAccessibilityService.overlayContext()
        val host = accessibility ?: app
        val screen = WebView(host)
        val type = if (accessibility != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= 26) {
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
            // Keep the host rendering/layout active as its track views change.
            // Dino's own page still paints the complete background.
            PixelFormat.TRANSLUCENT,
        )
        TvScreen.bind(screen, DinoTvBridge(app) { dismissFromRemote(app) }, TvPrefs.session(app))
        screen.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && isLeaveKey(keyCode)) {
                dismissFromRemote(app)
                true
            } else {
                false
            }
        }
        screen.isFocusable = true
        screen.isFocusableInTouchMode = true
        TvAudio.abandon(app)
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.addView(screen, params)
            windowManager = wm
            webView = screen
            TvForeground.visible = true
            screen.requestFocus()
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
        val wm = windowManager
        windowManager = null
        TvForeground.visible = false
        try {
            wm?.removeView(screen)
        } catch (_: Exception) {
            // Already gone.
        }
        screen.destroy()
    }

    private fun dismissFromRemote(app: Context) {
        TvPrefs.markUserDismissed(app)
        hide()
        ContextCompat.startForegroundService(
            app,
            Intent(app, TvWakeService::class.java).setAction(TvWakeService.ACTION_USER_DISMISS),
        )
    }

    private fun isLeaveKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_BUTTON_B,
        -> true
        else -> false
    }
}
