package com.dinotv.app.domain

import android.view.KeyEvent

data class MusicRemoteCommand(
    val action: String,
    val at: String,
    val volume: Int? = null,
)

object MusicRemote {
    private val actions = setOf("play", "pause", "toggle", "next", "previous", "volume")

    fun take(action: String, at: String, volume: Int? = null): MusicRemoteCommand? {
        if (action !in actions || at.isBlank()) return null
        return MusicRemoteCommand(action, at, volume?.coerceIn(0, 100))
    }

    /**
     * The key the Kinopoisk music player answers to. It has no media session and ignores
     * MEDIA_NEXT / MEDIA_PREVIOUS; only the D-pad of its own window moves it: right skips,
     * left goes back, OK pauses and resumes. Play and pause are one key there, so they are
     * pressed only when they would change something.
     */
    fun playerKey(action: String, playing: Boolean): Int? = when (action) {
        "next" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "previous" -> KeyEvent.KEYCODE_DPAD_LEFT
        "toggle" -> KeyEvent.KEYCODE_DPAD_CENTER
        "play" -> KeyEvent.KEYCODE_DPAD_CENTER.takeUnless { playing }
        "pause" -> KeyEvent.KEYCODE_DPAD_CENTER.takeIf { playing }
        else -> null
    }
}
