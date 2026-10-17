package com.dinotv.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var screen: WebView? = null
    @Volatile private var audioPermissionPromptQueued = false
    @Volatile private var audioPermissionPromptDismissed = false
    private val audioPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioPermissionPromptQueued = false
        if (granted) {
            audioPermissionPromptDismissed = false
            TvAudioVisualizer.snapshot(this)
        } else {
            audioPermissionPromptDismissed = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        askOverlayPermission()
        askNotificationAccess()
        TvPrefs.clearUserDismissed(this)
        startWakeService()
        if (showOverMusic()) return
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    leaveForTvRemote()
                }
            },
        )
        screen = WebView(this).also { webView ->
            TvScreen.bind(webView, DinoTvBridge(this) { leaveForTvRemote() }, TvPrefs.session(this))
            setContentView(webView)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Phone / console asked for Dino again — clear the local "stay away" window.
        TvPrefs.clearUserDismissed(this)
        startWakeService()
        showOverMusic()
    }

    private fun showOverMusic(): Boolean {
        if (!Settings.canDrawOverlays(this) || NowPlayingDesk.current(this) == null) return false
        LivingRoomOverlay.show(this)
        if (!LivingRoomOverlay.visible) return false
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        TvForeground.visible = true
        TvAudio.abandon(this)
        startWakeService()
        // The service reopens the question when someone asks for Dino over the music and
        // the permission turns out to be missing; this is where it can actually be asked.
        askOverlayPermission()
    }

    override fun onPause() {
        TvAudioVisualizer.release()
        if (!LivingRoomOverlay.visible) TvForeground.visible = false
        super.onPause()
    }

    override fun onDestroy() {
        TvAudioVisualizer.release()
        screen?.apply {
            stopLoading()
            removeJavascriptInterface("DinoTV")
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        screen = null
        super.onDestroy()
    }

    fun requestAudioVisualizerPermission() {
        if (audioPermissionPromptDismissed || audioPermissionPromptQueued) return
        audioPermissionPromptQueued = true
        runOnUiThread {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                audioPermissionPromptQueued = false
                TvAudioVisualizer.snapshot(this)
                return@runOnUiThread
            }
            AlertDialog.Builder(this)
                .setTitle("Эквалайзер")
                .setMessage(
                    "Чтобы полосы двигались в такт музыке, Dino TV считывает только уровни частот " +
                        "звука, который уже воспроизводится на телевизоре. Аудио не записывается и не отправляется.",
                )
                .setNegativeButton("Не сейчас") { _, _ ->
                    audioPermissionPromptQueued = false
                    audioPermissionPromptDismissed = true
                }
                .setPositiveButton("Продолжить") { _, _ ->
                    audioPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
                }
                .setOnCancelListener {
                    audioPermissionPromptQueued = false
                    audioPermissionPromptDismissed = true
                }
                .show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN && isLeaveKey(keyCode)) {
            leaveForTvRemote()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun startWakeService() {
        ContextCompat.startForegroundService(this, Intent(this, TvWakeService::class.java))
    }

    private fun leaveForTvRemote() {
        TvPrefs.markUserDismissed(this)
        LivingRoomOverlay.hide()
        ContextCompat.startForegroundService(
            this,
            Intent(this, TvWakeService::class.java).setAction(TvWakeService.ACTION_USER_DISMISS),
        )
        moveTaskToBack(true)
    }

    private fun isLeaveKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_BUTTON_B,
        -> true
        else -> false
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
        if (NotificationAccess.enabled(this) || TvPrefs.listenerPrompted(this)) return
        TvPrefs.markListenerPrompted(this)
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            // Android TV often hides this screen; ADB or the overlay still work without it.
        }
    }
}
