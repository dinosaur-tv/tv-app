package com.dinotv.app.domain

data class TvRemoteCommand(
    val action: String,
    val at: String,
    val app: String? = null,
    val key: String? = null,
)

object TvRemote {
    private val apps = setOf("kinopoisk", "dino")
    private val keys = setOf(
        "up", "down", "left", "right", "ok", "back", "home",
        "volume_up", "volume_down", "mute", "play_pause",
    )

    fun take(action: String, at: String, app: String? = null, key: String? = null): TvRemoteCommand? {
        if (at.isBlank()) return null
        return when (action) {
            "launch" -> if (app in apps) TvRemoteCommand(action, at, app = app) else null
            "key" -> if (key in keys) TvRemoteCommand(action, at, key = key) else null
            else -> null
        }
    }

    fun after(lastAt: String, commands: List<TvRemoteCommand>): List<TvRemoteCommand> =
        commands.filter { it.at > lastAt }.sortedBy { it.at }
}
