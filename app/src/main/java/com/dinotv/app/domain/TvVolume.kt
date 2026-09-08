package com.dinotv.app.domain

import kotlin.math.roundToInt

object TvVolume {
    fun percent(current: Int, max: Int): Int {
        if (max <= 0) return 50
        return (100.0 * current.coerceIn(0, max) / max).roundToInt().coerceIn(0, 100)
    }

    fun streamValue(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        return (max * percent.coerceIn(0, 100) / 100.0).roundToInt().coerceIn(0, max)
    }
}
