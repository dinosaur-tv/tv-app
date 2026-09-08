package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TvVolumeTest {
    @Test
    fun `maps the television stream onto a 0-100 slider`() {
        assertEquals(0, TvVolume.percent(0, 15))
        assertEquals(100, TvVolume.percent(15, 15))
        assertEquals(53, TvVolume.percent(8, 15))
    }

    @Test
    fun `sets the nearest hardware step instead of truncating down`() {
        assertEquals(8, TvVolume.streamValue(50, 15))
        assertEquals(8, TvVolume.streamValue(53, 15))
        assertEquals(15, TvVolume.streamValue(100, 15))
        assertEquals(0, TvVolume.streamValue(2, 15))
    }
}
