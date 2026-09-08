package com.dinotv.app.domain

object TvWakePolicy {
    fun keepHostPlaying(musicActive: Boolean, overlayAllowed: Boolean, hasNowPlaying: Boolean = false): Boolean =
        overlayAllowed && (musicActive || hasNowPlaying)
}
