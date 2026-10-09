package com.dinotv.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sqrt

/** Reads a small, local-only FFT snapshot from the TV output mix for the now-playing display. */
object TvAudioVisualizer {
    const val PERMISSION_REQUIRED = "permission-required"
    private const val ACTIVE = "active"
    private const val UNAVAILABLE = "unavailable"
    private const val IDLE = "idle"
    private const val BAND_COUNT = 5
    private const val CAPTURE_RATE = 20_000 // milliHz, about 20 FFT snapshots per second
    private val lock = Any()

    @Volatile private var visualizer: Visualizer? = null
    @Volatile private var state = IDLE
    @Volatile private var levels = FloatArray(BAND_COUNT)

    /** Enables capture only while music is playing and the user has granted audio permission. */
    fun snapshot(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (state != PERMISSION_REQUIRED) {
                release()
                state = PERMISSION_REQUIRED
            }
            return json()
        }

        synchronized(lock) {
            if (visualizer == null && state != UNAVAILABLE) {
                try {
                    val capture = Visualizer(0)
                    val captureRange = Visualizer.getCaptureSizeRange()
                    capture.captureSize = captureRange.last()
                    capture.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                    val rate = CAPTURE_RATE.coerceAtMost(Visualizer.getMaxCaptureRate()).coerceAtLeast(1)
                    capture.setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int,
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int,
                            ) {
                                if (fft != null) levels = levelsFrom(fft, samplingRate)
                            }
                        },
                        rate,
                        false,
                        true,
                    )
                    capture.enabled = true
                    visualizer = capture
                    state = ACTIVE
                } catch (_: Exception) {
                    state = UNAVAILABLE
                    levels = FloatArray(BAND_COUNT)
                }
            }
        }
        return json()
    }

    fun release() {
        synchronized(lock) {
            visualizer?.let { capture ->
                try {
                    capture.enabled = false
                    capture.setDataCaptureListener(null, 0, false, false)
                    capture.release()
                } catch (_: Exception) {
                    // Some TV audio HALs release the global output effect during shutdown.
                }
            }
            visualizer = null
            levels = FloatArray(BAND_COUNT)
            state = IDLE
        }
    }

    private fun levelsFrom(fft: ByteArray, samplingRateMilliHz: Int): FloatArray {
        val bins = fft.size / 2
        if (bins < 2 || samplingRateMilliHz <= 0) return FloatArray(BAND_COUNT)
        val sampleRate = samplingRateMilliHz / 1_000.0
        val edgesHz = doubleArrayOf(40.0, 150.0, 500.0, 1_600.0, 5_000.0, 12_000.0)
        val previous = levels
        return FloatArray(BAND_COUNT) { band ->
            val first = floor(edgesHz[band] * fft.size / sampleRate).toInt().coerceIn(1, bins - 1)
            val last = floor(edgesHz[band + 1] * fft.size / sampleRate).toInt().coerceIn(first + 1, bins)
            var sum = 0.0
            var count = 0
            for (index in first until last) {
                val real = fft[index * 2].toDouble()
                val imaginary = fft[index * 2 + 1].toDouble()
                sum += sqrt(real * real + imaginary * imaginary)
                count++
            }
            val magnitude = (sum / count.coerceAtLeast(1)).coerceAtLeast(0.01)
            val decibels = 20.0 * log10(magnitude / 127.0)
            val target = ((decibels + 52.0) / 44.0).coerceIn(0.0, 1.0).toFloat()
            val old = previous.getOrElse(band) { 0f }
            val smoothing = if (target > old) 0.58f else 0.16f
            old + (target - old) * smoothing
        }
    }

    private fun json(): String {
        val formatted = levels.joinToString(",") { "%.3f".format(java.util.Locale.US, it) }
        return """{"state":"$state","bands":[$formatted]}"""
    }
}
