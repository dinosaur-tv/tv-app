package com.dinotv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat

/** Activity windows use KEEP_SCREEN_ON; service overlays need an explicit, bounded lease on some TVs. */
internal object OverlayScreenAwake {
    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var lease: PowerManager.WakeLock? = null
    private val tick = object : Runnable {
        override fun run() {
            val app = context ?: return
            val power = app.getSystemService(PowerManager::class.java)
            if (power.isInteractive && LivingRoomOverlay.visible) lease?.acquire(120_000L)
            else release()
            handler.postDelayed(this, 30_000L)
        }
    }
    private val screenChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) release()
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                handler.removeCallbacks(tick)
                tick.run()
            }
        }
    }

    @Suppress("DEPRECATION") // No Activity can be raised without pausing the host music player.
    fun start(appContext: Context) {
        if (context != null) return
        val app = appContext.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        lease = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "dinotv:visible-overlay").apply {
            setReferenceCounted(false)
        }
        ContextCompat.registerReceiver(app, screenChanges, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        context = app
        tick.run()
    }

    fun stop() {
        handler.removeCallbacks(tick)
        val app = context
        context = null
        if (app != null) app.unregisterReceiver(screenChanges)
        release()
        lease = null
    }

    private fun release() {
        if (lease?.isHeld == true) lease?.release()
    }
}
