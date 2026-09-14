package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNameTest {
    @Test
    fun `puts the make in front of the model`() {
        assertEquals("LG 43UQ81006LB", ScreenName.of("LG", "43UQ81006LB"))
    }

    @Test
    fun `capitalises a make the vendor wrote in lower case`() {
        assertEquals("Xiaomi MIBOX4", ScreenName.of("xiaomi", "MIBOX4"))
    }

    @Test
    fun `does not say the make twice when the model already carries it`() {
        assertEquals("LG-43UQ81", ScreenName.of("LG", "LG-43UQ81"))
        assertEquals("Samsung QE55", ScreenName.of("Samsung", "Samsung QE55"))
    }

    @Test
    fun `copes with a television that reports only half of itself`() {
        assertEquals("Sony", ScreenName.of("Sony", "  "))
        assertEquals("AFTKA", ScreenName.of("", "AFTKA"))
        assertEquals("", ScreenName.of("", ""))
    }

    @Test
    fun `stays short enough for the field it lands in`() {
        assertEquals(40, ScreenName.of("Manufacturer", "M".repeat(60)).length)
    }
}
