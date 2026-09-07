package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayThemeTest {
    @Test
    fun `resolves every server theme identifier`() {
        assertEquals(DisplayTheme.GALLERY, DisplayTheme.fromId("gallery"))
        assertEquals(DisplayTheme.TOBACCO, DisplayTheme.fromId("tobacco"))
        assertEquals(DisplayTheme.APPLE, DisplayTheme.fromId("apple"))
    }

    @Test
    fun `returns null for an unsupported theme`() {
        assertNull(DisplayTheme.fromId("neon"))
    }
}
