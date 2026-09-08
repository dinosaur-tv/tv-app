package com.dinotv.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.dinotv.app.domain.EventCue
import com.dinotv.app.domain.EventReminders
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object EventOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var hideAt: Runnable? = null

    fun show(context: Context, cue: EventCue) {
        if (!Settings.canDrawOverlays(context)) return
        hide()
        val app = context.applicationContext
        val card = LayoutInflater.from(app).inflate(R.layout.overlay_event, null)
        card.findViewById<TextView>(R.id.eventCueWhen).text = EventReminders.minutesPhrase(cue.minutes)
        card.findViewById<TextView>(R.id.eventCueTitle).text = cue.title
        card.findViewById<TextView>(R.id.eventCueMeta).text = listOfNotNull(
            cue.owner.takeIf { it.isNotBlank() },
            clock(cue.start),
        ).joinToString(" · ")
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.END
        val margin = (48 * app.resources.displayMetrics.density).toInt()
        params.x = margin
        params.y = margin
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.addView(card, params)
            view = card
            val hide = Runnable { hide() }
            hideAt = hide
            main.postDelayed(hide, 12_000)
        } catch (_: Exception) {
            // Overlay permission can be withdrawn while the movie is playing.
        }
    }

    fun hide() {
        hideAt?.let(main::removeCallbacks)
        hideAt = null
        val card = view ?: return
        view = null
        try {
            (card.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(card)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private fun clock(start: String): String? =
        try {
            OffsetDateTime.parse(start).format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            null
        }
}
