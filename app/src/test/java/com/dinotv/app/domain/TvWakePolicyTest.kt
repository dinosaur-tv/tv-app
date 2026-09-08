package com.dinotv.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvWakePolicyTest {
    @Test
    fun `keeps Kinopoisk playing when music is already on and overlay is allowed`() {
        assertTrue(TvWakePolicy.keepHostPlaying(musicActive = true, overlayAllowed = true))
    }

    @Test
    fun `opens Dino as an activity when nothing is playing`() {
        assertFalse(TvWakePolicy.keepHostPlaying(musicActive = false, overlayAllowed = true))
    }

    @Test
    fun `cannot keep the host playing without overlay permission`() {
        assertFalse(TvWakePolicy.keepHostPlaying(musicActive = true, overlayAllowed = false))
    }

    @Test
    fun `overlays even when the track is paused if Kinopoisk still has a session`() {
        assertTrue(TvWakePolicy.keepHostPlaying(musicActive = false, overlayAllowed = true, hasNowPlaying = true))
    }
}
