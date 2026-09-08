package com.dinotv.app.domain

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
}
