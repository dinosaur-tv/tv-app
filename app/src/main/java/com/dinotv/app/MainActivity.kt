package com.dinotv.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        askOverlayPermission()
        askNotificationAccess()
        startWakeService()
        val screen = WebView(this)
        TvScreen.bind(screen, DinoTvBridge(this) { finish() }, TvPrefs.session(this))
        setContentView(screen)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startWakeService()
    }

    override fun onResume() {
        super.onResume()
        TvForeground.visible = true
        TvAudio.abandon(this)
        startWakeService()
    }

    override fun onPause() {
        if (!LivingRoomOverlay.visible) TvForeground.visible = false
        super.onPause()
    }

    fun startWakeService() {
        ContextCompat.startForegroundService(this, Intent(this, TvWakeService::class.java))
    }

    private fun askOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this) || TvPrefs.overlayPrompted(this)) return
        TvPrefs.markOverlayPrompted(this)
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            // Some Android TV builds hide this screen; the overlay simply will not appear.
        }
    }

    private fun askNotificationAccess() {
        if (TvPrefs.listenerPrompted(this)) return
        TvPrefs.markListenerPrompted(this)
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            // Android TV often hides this screen; ADB or the overlay still work without it.
        }
    }
}
