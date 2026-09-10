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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import com.dinotv.app.domain.EventCue
import com.dinotv.app.domain.EventReminders
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** A quiet card in the corner of the film: it slides in, drains its line and leaves on its own. */
object EventOverlay {
    /** Long enough to read twice, short enough not to sit on the film. */
    private const val DWELL_MS = 20_000L
    private const val ENTER_MS = 460L
    private const val LEAVE_MS = 320L

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var hideAt: Runnable? = null
    private var activeCueId: String? = null

    fun show(context: Context, cue: EventCue) {
        if (!Settings.canDrawOverlays(context)) return
        if (view != null && activeCueId == cue.id) return
        hide()
        val app = context.applicationContext
        val root = LayoutInflater.from(app).inflate(R.layout.overlay_event, null)
        val card = root.findViewById<View>(R.id.eventCueCard)
        val progress = root.findViewById<View>(R.id.eventCueProgress)
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Without this the card's shadow and the fades fall back to software drawing.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.END
        val margin = (22 * app.resources.displayMetrics.density).toInt()
        params.x = margin
        params.y = margin
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.addView(root, params)
            view = root
            activeCueId = cue.id
            enter(card, progress, app.resources.displayMetrics.density)
            val leave = Runnable { dismiss() }
            hideAt = leave
            main.postDelayed(leave, DWELL_MS)
            EventChime.play()
        } catch (_: Exception) {
            // Overlay permission can be withdrawn while the movie is playing.
        }
    }

    /** Fades the card out and then removes it; safe to call when nothing is on screen. */
    fun dismiss() {
        val root = view ?: return
        val card = root.findViewById<View>(R.id.eventCueCard) ?: return hide()
        hideAt?.let(main::removeCallbacks)
        hideAt = null
        EventChime.stop()
        card.animate()
            .alpha(0f)
            .translationX(card.width * 0.06f)
            .setDuration(LEAVE_MS)
            // A later cue may have replaced this card while it was fading.
            .withEndAction { if (view === root) hide() }
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    fun hide() {
        EventChime.stop()
        activeCueId = null
        hideAt?.let(main::removeCallbacks)
        hideAt = null
        val root = view ?: return
        view = null
        try {
            (root.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(root)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private fun enter(card: View, progress: View, density: Float) {
        card.alpha = 0f
        card.translationX = 40 * density
        card.scaleX = 0.97f
        card.scaleY = 0.97f
        card.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
        progress.pivotX = 0f
        progress.scaleX = 1f
        progress.animate()
            .scaleX(0f)
            .setStartDelay(ENTER_MS)
            .setDuration(DWELL_MS - ENTER_MS)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun clock(start: String): String? =
        try {
            OffsetDateTime.parse(start).format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            null
        }
}
