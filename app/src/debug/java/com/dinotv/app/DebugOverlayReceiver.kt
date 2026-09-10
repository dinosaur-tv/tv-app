package com.dinotv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class DebugOverlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, Intent(context, TvWakeService::class.java))
        when {
            intent.getBooleanExtra("eventCue", false) -> {
                EventOverlay.show(context, com.dinotv.app.domain.EventCue(
                    id = "debug-reminder-${android.os.SystemClock.uptimeMillis()}",
                    title = "Проверка уведомления",
                    owner = "Dino TV",
                    start = java.time.OffsetDateTime.now().plusMinutes(5).toString(),
                    minutes = 5,
                ))
            }
            intent.hasExtra("launch") -> {
                RemoteCursor.hide()
                val app = intent.getStringExtra("launch").orEmpty()
                val open = TvApps.intent(context, app)
                if (open != null) {
                    context.startActivity(open)
                    android.util.Log.i("DebugPad", "launch=$app ok")
                } else {
                    android.util.Log.w("DebugPad", "launch=$app missing intent")
                }
            }
            intent.hasExtra("key") -> {
                val key = intent.getStringExtra("key").orEmpty()
                RemoteCursor.hide()
                when (key) {
                    "up", "down", "left", "right" -> {
                        val moved = RemoteAccessibilityService.moveFocus(key)
                        val pressed = if (!moved) RemoteAccessibilityService.press(key) else false
                        android.util.Log.i("DebugPad", "key=$key moved=$moved pressed=$pressed")
                    }
                    else -> {
                        val ok = RemoteAccessibilityService.press(key)
                        android.util.Log.i("DebugPad", "key=$key handled=$ok")
                    }
                }
            }
            intent.getBooleanExtra("cursor", false) -> {
                RemoteCursor.hide()
            }
            intent.getBooleanExtra("hide", false) -> {
                RemoteCursor.hide()
                LivingRoomOverlay.hide()
            }
            intent.getBooleanExtra("nowPlaying", false) -> {
                NotificationAccess.ensureEnabled(context)
                val track = NowPlayingDesk.current(context)
                android.util.Log.i(
                    "NowPlaying",
                    "debug connected=${NowPlayingListener.connected()} " +
                        "enabled=${NotificationAccess.enabled(context)} " +
                        "notifs=${NowPlayingListener.activeNotifications()?.size} " +
                        "musicActive=${TvAudio.isPlaying(context)} " +
                        "track=${track?.toReportJson() ?: "null"}",
                )
            }
            intent.getBooleanExtra("rail", false) -> {
                android.util.Log.i("DebugPad", RemoteAccessibilityService.debugRail())
            }
            else -> LivingRoomOverlay.show(context)
        }
    }
}
