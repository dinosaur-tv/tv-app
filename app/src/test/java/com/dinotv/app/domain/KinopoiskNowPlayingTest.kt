package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KinopoiskNowPlayingTest {
    private val player = listOf(
        KinopoiskNowPlaying.Node(description = "Build A Bridge", left = 710, top = 192, right = 1210, bottom = 692),
        KinopoiskNowPlaying.Node(text = "Build A Bridge", left = 420, top = 732, right = 1500, bottom = 785),
        KinopoiskNowPlaying.Node(text = "Limp Bizkit, Brian Welch", left = 420, top = 793, right = 1500, bottom = 831),
        KinopoiskNowPlaying.Node(text = "Сейчас играет", left = 90, top = 90, right = 274, bottom = 122),
        KinopoiskNowPlaying.Node(text = "Моя волна", left = 90, top = 130, right = 257, bottom = 168),
    )

    @Test
    fun `reads the real player captured on Xiaomi even without a MediaSession`() {
        assertEquals(
            KinopoiskNowPlaying.Track("Build A Bridge", "Limp Bizkit, Brian Welch"),
            KinopoiskNowPlaying.read("ru.kinopoisk.tv", player),
        )
    }

    @Test
    fun `reads an explicit track whose title ends with the E badge`() {
        val explicit = listOf(
            KinopoiskNowPlaying.Node(description = "7143", left = 710, top = 191, right = 1210, bottom = 691),
            KinopoiskNowPlaying.Node(text = "7143 " + Char(0xFFFD), left = 878, top = 731, right = 1043, bottom = 785),
            KinopoiskNowPlaying.Node(text = "TTRI", left = 420, top = 793, right = 1500, bottom = 831),
            KinopoiskNowPlaying.Node(text = "Сейчас играет", left = 90, top = 90, right = 274, bottom = 122),
        )
        assertEquals(KinopoiskNowPlaying.Track("7143", "TTRI"), KinopoiskNowPlaying.read("ru.kinopoisk.tv", explicit))
    }

    @Test
    fun `does not report a film or a music recommendation as the current track`() {
        assertNull(KinopoiskNowPlaying.read("ru.kinopoisk.tv", player.filter { it.text != "Сейчас играет" }))
        assertNull(KinopoiskNowPlaying.read("another.app", player))
        assertNull(KinopoiskNowPlaying.read("ru.kinopoisk.tv", player.filter { it.description.isEmpty() }))
    }

    @Test
    fun `missing artist stays empty instead of using a header or a playlist`() {
        assertEquals("", KinopoiskNowPlaying.read("ru.kinopoisk.tv", player.filter { !it.text.contains("Limp") })?.artist)
    }
}
