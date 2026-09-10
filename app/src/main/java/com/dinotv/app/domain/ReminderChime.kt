package com.dinotv.app.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Original two-note chime: a slow attack so it never startles over a film, an ascending
 * fourth, and a long bell tail that fades to silence. No external audio assets.
 */
object ReminderChime {
    const val SAMPLE_RATE = 24_000
    const val DURATION_MS = 900

    private const val ATTACK_S = 0.030
    private const val DECAY = 5.4
    private const val TAIL_S = 0.18
    private const val SECOND_NOTE_S = 0.20

    fun samples(): ShortArray {
        val count = SAMPLE_RATE * DURATION_MS / 1_000
        return ShortArray(count) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val remaining = (count - 1 - index).toDouble() / SAMPLE_RATE
            val fade = 0.5 - 0.5 * cos(PI * (remaining / TAIL_S).coerceIn(0.0, 1.0))
            val signal = bell(time, 659.255, 0.26) + bell(time - SECOND_NOTE_S, 880.0, 0.24)
            (signal * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun bell(age: Double, frequency: Double, level: Double): Double {
        if (age < 0) return 0.0
        val attack = 0.5 - 0.5 * cos(PI * (age / ATTACK_S).coerceAtMost(1.0))
        val phase = 2 * PI * frequency * age
        // A faint octave above and two inharmonic partials give the tone a struck-bell body.
        return level * attack * exp(-DECAY * age) *
            (sin(phase) + 0.12 * sin(phase * 2.003) + 0.05 * sin(phase * 2.76) + 0.02 * sin(phase * 4.07))
    }
}
