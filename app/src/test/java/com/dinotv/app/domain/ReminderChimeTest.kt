package com.dinotv.app.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderChimeTest {
    @Test
    fun `notification lasts less than a second`() {
        assertTrue(ReminderChime.DURATION_MS in 500..999)
        assertEquals(ReminderChime.SAMPLE_RATE * ReminderChime.DURATION_MS / 1_000, ReminderChime.samples().size)
    }

    @Test
    fun `bell is audible without clipping or excessive peaks`() {
        val samples = ReminderChime.samples()
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue(peak in 5_000..18_000)
        val mean = samples.sumOf { it.toDouble() } / samples.size
        assertTrue(abs(mean) < 10)
    }

    @Test
    fun `attack and tail fade to silence without a hard cut`() {
        val samples = ReminderChime.samples()
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.take(24).maxOf { abs(it.toInt()) } < 100)
        assertTrue(samples.takeLast(240).maxOf { abs(it.toInt()) } < 20)
    }

    @Test
    fun `both notes sound before the short decay`() {
        val samples = ReminderChime.samples()
        fun energy(fromMs: Int, toMs: Int): Double =
            samples.sliceArray(fromMs * ReminderChime.SAMPLE_RATE / 1_000 until toMs * ReminderChime.SAMPLE_RATE / 1_000)
                .map { it.toDouble() * it }.average()
        assertTrue(energy(20, 100) > 1_000_000)
        assertTrue(energy(180, 250) > 1_000_000)
        assertTrue(energy(700, 810) < energy(180, 250) * 0.01)
    }
}
