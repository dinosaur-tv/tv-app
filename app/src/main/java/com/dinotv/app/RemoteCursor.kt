package com.dinotv.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Fallback pointer when real DPAD injection is unavailable.
 * Quiet crosshair — not a loud red blob.
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
        val metrics = metrics(app)
        if (!ready) {
            x = metrics.widthPixels * 0.37f
            y = metrics.heightPixels * 0.50f
            ready = true
        }
        val density = metrics.density
        val size = (density * 28).toInt().coerceIn(36, 56)
        val cross = object : View(app) {
            private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = density * 1.6f
                color = Color.argb(200, 245, 245, 240)
            }
            private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(210, 255, 196, 72)
            }
            private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = density * 1.2f
                color = Color.argb(160, 255, 255, 255)
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = width * 0.28f
                val arm = width * 0.42f
                canvas.drawLine(cx - arm, cy, cx - r * 0.7f, cy, hair)
                canvas.drawLine(cx + r * 0.7f, cy, cx + arm, cy, hair)
                canvas.drawLine(cx, cy - arm, cx, cy - r * 0.7f, hair)
                canvas.drawLine(cx, cy + r * 0.7f, cx, cy + arm, hair)
                canvas.drawCircle(cx, cy, r, ring)
                canvas.drawCircle(cx, cy, density * 2.2f, core)
            }
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
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(cross, params)
            view = cross
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
        setPosition(
            context,
            (x + dx).coerceIn(inset, metrics.widthPixels - inset),
            (y + dy).coerceIn(inset, metrics.heightPixels - inset),
        )
    }

    fun setPosition(context: Context, nextX: Float, nextY: Float) {
        ensure(context)
        val metrics = metrics(context.applicationContext)
        val inset = 24f
        x = nextX.coerceIn(inset, metrics.widthPixels - inset)
        y = nextY.coerceIn(inset, metrics.heightPixels - inset)
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
        ready = false
        try {
            (dot.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(dot)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private val hideRunnable = Runnable { hide() }

    private fun bumpHide() {
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, 5_000)
    }

    @SuppressLint("Deprecated")
    private fun metrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }
}
