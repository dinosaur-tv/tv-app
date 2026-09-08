package com.dinotv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class DebugOverlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, Intent(context, TvWakeService::class.java))
        when {
            intent.getBooleanExtra("cursor", false) -> {
                RemoteCursor.move(context, 120f, 0f)
            }
            intent.getBooleanExtra("hide", false) -> LivingRoomOverlay.hide()
            else -> LivingRoomOverlay.show(context)
        }
    }
}
