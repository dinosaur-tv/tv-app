package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayThemeTest {
    @Test
    fun `resolves every server theme identifier`() {
        assertEquals(DisplayTheme.GALLERY, DisplayTheme.fromId("gallery"))
        assertEquals(DisplayTheme.NIGHT, DisplayTheme.fromId("night"))
        assertEquals(DisplayTheme.PLAY, DisplayTheme.fromId("play"))
        assertEquals(DisplayTheme.FOREST, DisplayTheme.fromId("forest"))
        assertEquals(DisplayTheme.MOUNTAINS, DisplayTheme.fromId("mountains"))
        assertEquals(DisplayTheme.SEA, DisplayTheme.fromId("sea"))
        assertEquals(DisplayTheme.SPACE, DisplayTheme.fromId("space"))
    }

    @Test
    fun `returns null for an unsupported theme`() {
        assertNull(DisplayTheme.fromId("neon"))
    }
}
