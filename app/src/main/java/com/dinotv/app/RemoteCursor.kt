package com.dinotv.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Software DPAD cursor for apps (Kinopoisk Compose) that ignore accessibility focus.
 * Arrows move the dot; OK taps underneath via [RemoteAccessibilityService].
 */
object RemoteCursor {
    private const val TAG = "RemoteCursor"
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var x = 0f
    private var y = 0f
    private var ready = false

    val pointX: Float get() = x
    val pointY: Float get() = y
    val visible: Boolean get() = view != null

    fun ensure(context: Context) {
        if (view != null) {
            bumpHide()
            return
        }
        val app = context.applicationContext
        // MiTV sometimes reports canDrawOverlays=false while appops already allows it.
        // Always attempt addView and log the failure.
        val metrics = metrics(app)
        if (!ready) {
            x = metrics.widthPixels / 2f
            y = metrics.heightPixels / 2f
            ready = true
        }
        val dot = View(app)
        val size = (metrics.density * 36).toInt().coerceIn(48, 80)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(230, 255, 60, 60))
            setStroke((metrics.density * 3).toInt().coerceAtLeast(3), Color.WHITE)
        }
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - size / 2f).toInt()
            this.y = (y - size / 2f).toInt()
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(dot, params)
            view = dot
            Log.i(TAG, "cursor shown at $x,$y size=$size")
            bumpHide()
        } catch (error: Exception) {
            Log.e(TAG, "addView failed", error)
        }
    }

    fun move(context: Context, dx: Float, dy: Float) {
        ensure(context)
        val metrics = metrics(context.applicationContext)
        val inset = 24f
        x = (x + dx).coerceIn(inset, metrics.widthPixels - inset)
        y = (y + dy).coerceIn(inset, metrics.heightPixels - inset)
        val dot = view
        if (dot == null) {
            Log.w(TAG, "move ignored; cursor not visible")
            return
        }
        val params = dot.layoutParams as WindowManager.LayoutParams
        params.x = (x - params.width / 2f).toInt()
        params.y = (y - params.height / 2f).toInt()
        try {
            (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .updateViewLayout(dot, params)
            Log.i(TAG, "cursor -> $x,$y")
        } catch (error: Exception) {
            Log.e(TAG, "updateViewLayout failed", error)
            hide()
        }
        bumpHide()
    }

    fun hide() {
        main.removeCallbacks(hideRunnable)
        val dot = view ?: return
        view = null
        try {
            (dot.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(dot)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private val hideRunnable = Runnable { hide() }

    private fun bumpHide() {
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, 8_000)
    }

    @SuppressLint("Deprecated")
    private fun metrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }
}
