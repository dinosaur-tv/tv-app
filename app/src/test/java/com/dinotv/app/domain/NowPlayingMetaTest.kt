package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingMetaTest {
    @Test
    fun `reads a Kinopoisk track for the living-room bar`() {
        val track = NowPlayingMeta.from(
            "ru.kinopoisk.tv",
            "The Unforgiven",
            "Metallica",
            "Metallica",
            true,
        )
        assertEquals("The Unforgiven", track?.title)
        assertEquals("Metallica", track?.artist)
        assertEquals("Яндекс Музыка", track?.source)
        assertEquals(true, track?.isPlaying)
        assertNull(track?.volumePercent)
    }

    @Test
    fun `falls back to the album when the artist is missing`() {
        val track = NowPlayingMeta.from("ru.yandex.music", "Chica", "  ", "Dos Ton", true)
        assertEquals("Dos Ton", track?.artist)
    }

    @Test
    fun `ignores a session without a title`() {
        assertNull(NowPlayingMeta.from("ru.kinopoisk.tv", "  ", "Metallica", null, true))
    }

    @Test
    fun `keeps a playing untitled session alive for the remote`() {
        val track = NowPlayingMeta.from(
            "ru.kinopoisk.tv",
            "  ",
            null,
            null,
            true,
            allowUntitled = true,
        )
        assertEquals("Яндекс Музыка", track?.title)
        assertEquals(true, track?.isPlaying)
    }

    @Test
    fun `scales volume only when the stream actually reports it`() {
        assertEquals(40, NowPlayingMeta.from("ru.kinopoisk.tv", "Chica", "Dos Ton", null, true, 4, 10)?.volumePercent)
        assertNull(NowPlayingMeta.from("ru.kinopoisk.tv", "Chica", "Dos Ton", null, true, 0, 0)?.volumePercent)
    }

    @Test
    fun `escapes quotes in the bridge payload`() {
        val json = NowPlayingTrack("He said \"hi\"", "A\\B", "Яндекс Музыка", true).toJson()
        assertEquals(
            """{"title":"He said \"hi\"","artist":"A\\B","source":"Яндекс Музыка","isPlaying":true,"artworkUrl":"","deviceName":"Телевизор"}""",
            json,
        )
    }

    @Test
    fun `drops bitmap artwork from the payload sent to the house server`() {
        val json = NowPlayingTrack("GANG", "Индаблэк", "Яндекс Музыка", true, artworkUrl = "data:image/jpeg;base64,xx").toReportJson()
        assertEquals(
            """{"title":"GANG","artist":"Индаблэк","source":"Яндекс Музыка","isPlaying":true,"artworkUrl":"","deviceName":"Телевизор"}""",
            json,
        )
    }
}
