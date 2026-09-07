package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayThemeTest {
    @Test
    fun `resolves every server theme identifier`() {
        assertEquals(DisplayTheme.GALLERY, DisplayTheme.fromId("gallery"))
        assertEquals(DisplayTheme.HOME_DAY, DisplayTheme.fromId("home-day"))
        assertEquals(DisplayTheme.HOME_EVENING, DisplayTheme.fromId("home-evening"))
        assertEquals(DisplayTheme.NIGHT, DisplayTheme.fromId("night"))
        assertEquals(DisplayTheme.PLAY, DisplayTheme.fromId("play"))
        assertEquals(DisplayTheme.FOREST, DisplayTheme.fromId("forest"))
        assertEquals(DisplayTheme.MOUNTAINS, DisplayTheme.fromId("mountains"))
        assertEquals(DisplayTheme.SEA, DisplayTheme.fromId("sea"))
        assertEquals(DisplayTheme.SPACE, DisplayTheme.fromId("space"))
        assertEquals(DisplayTheme.PETERSBURG, DisplayTheme.fromId("petersburg"))
        assertEquals(DisplayTheme.ROME, DisplayTheme.fromId("rome"))
        assertEquals(DisplayTheme.FLORENCE, DisplayTheme.fromId("florence"))
        assertEquals(DisplayTheme.VENICE, DisplayTheme.fromId("venice"))
    }

    @Test
    fun `returns null for an unsupported theme`() {
        assertNull(DisplayTheme.fromId("neon"))
    }

    @Test
    fun `wakes the television only when a new power token arrives`() {
        assertEquals(false, TvPower.shouldApply("", ""))
        assertEquals(true, TvPower.shouldApply("", "2026-09-07T21:00:00.000Z"))
        assertEquals(false, TvPower.shouldApply("2026-09-07T21:00:00.000Z", "2026-09-07T21:00:00.000Z"))
        assertEquals(true, TvPower.shouldApply("2026-09-07T21:00:00.000Z", "2026-09-07T21:00:01.000Z"))
    }
}
