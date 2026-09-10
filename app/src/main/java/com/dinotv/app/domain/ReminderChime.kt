package com.dinotv.app.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Original two-note chime. A quiet octave below gives it body, each note carries a barely
 * detuned twin for a slow shimmer, and one early reflection puts it in a room instead of
 * a speaker. Slow attack so it never startles over a film. No external audio assets.
 */
object ReminderChime {
    const val SAMPLE_RATE = 24_000
    const val DURATION_MS = 980

    private const val ATTACK_S = 0.034
    private const val DECAY = 5.0
    private const val TAIL_S = 0.20
    private const val SHIMMER = 1.001_734 // three cents
    private const val SHIMMER_LEVEL = 0.42
    private const val REFLECTION_S = 0.072
    private const val REFLECTION_LEVEL = 0.26

    /** Start second, frequency, level. The low E is body, not a note you should pick out. */
    private val notes = listOf(
        Triple(0.00, 329.628, 0.10),
        Triple(0.00, 659.255, 0.23),
        Triple(0.19, 880.000, 0.21),
    )

    fun samples(): ShortArray {
        val count = SAMPLE_RATE * DURATION_MS / 1_000
        return ShortArray(count) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val remaining = (count - 1 - index).toDouble() / SAMPLE_RATE
            val fade = 0.5 - 0.5 * cos(PI * (remaining / TAIL_S).coerceIn(0.0, 1.0))
            val signal = voice(time) + REFLECTION_LEVEL * voice(time - REFLECTION_S)
            (signal * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun voice(time: Double): Double {
        var total = 0.0
        for ((start, frequency, level) in notes) {
            total += bell(time - start, frequency, level)
            total += bell(time - start, frequency * SHIMMER, level * SHIMMER_LEVEL)
        }
        return total
    }

    private fun bell(age: Double, frequency: Double, level: Double): Double {
        if (age < 0) return 0.0
        val attack = 0.5 - 0.5 * cos(PI * (age / ATTACK_S).coerceAtMost(1.0))
        val phase = 2 * PI * frequency * age
        // Softer partials than a struck bell: enough overtone to carry, not enough to glare.
        return level * attack * exp(-DECAY * age) *
            (sin(phase) + 0.09 * sin(phase * 2.0) + 0.035 * sin(phase * 2.99) + 0.012 * sin(phase * 4.2))
    }
}
