package com.dinotv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class DebugOverlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, Intent(context, TvWakeService::class.java))
        when {
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
                when (key) {
                    "up", "down", "left", "right" -> {
                        if (RemoteAccessibilityService.needsPointer()) {
                            if (!RemoteCursor.visible) {
                                val metrics = context.resources.displayMetrics
                                RemoteCursor.setPosition(
                                    context,
                                    metrics.widthPixels * 0.37f,
                                    metrics.heightPixels * 0.50f,
                                )
                            }
                            val step = minOf(
                                context.resources.displayMetrics.widthPixels,
                                context.resources.displayMetrics.heightPixels,
                            ) * 0.06f
                            RemoteCursor.move(
                                context,
                                when (key) {
                                    "left" -> -step
                                    "right" -> step
                                    else -> 0f
                                },
                                when (key) {
                                    "up" -> -step
                                    "down" -> step
                                    else -> 0f
                                },
                            )
                            RemoteAccessibilityService.snapCursor()
                            if (key == "up" || key == "down") {
                                RemoteAccessibilityService.scrollByPad(key)
                            }
                        } else {
                            RemoteCursor.hide()
                            if (!RemoteAccessibilityService.moveFocus(key)) {
                                RemoteAccessibilityService.press(key)
                            }
                        }
                        android.util.Log.i(
                            "DebugPad",
                            "key=$key pointer=${RemoteAccessibilityService.needsPointer()} cursor=${RemoteCursor.visible}",
                        )
                    }
                    "ok" -> {
                        if (!RemoteAccessibilityService.needsPointer()) RemoteCursor.hide()
                        val ok = RemoteAccessibilityService.press("ok")
                        android.util.Log.i("DebugPad", "key=ok handled=$ok cursor=${RemoteCursor.visible}")
                    }
                    else -> {
                        RemoteCursor.hide()
                        val ok = RemoteAccessibilityService.press(key)
                        android.util.Log.i("DebugPad", "key=$key handled=$ok")
                    }
                }
            }
            intent.getBooleanExtra("cursor", false) -> {
                RemoteCursor.move(context, 120f, 0f)
            }
            intent.getBooleanExtra("hide", false) -> {
                RemoteCursor.hide()
                LivingRoomOverlay.hide()
            }
            else -> LivingRoomOverlay.show(context)
        }
    }
}
