package com.dinotv.app.domain

/** Only the full-screen music player, never film cards or the music recommendations page. */
object KinopoiskNowPlaying {
    data class Node(
        val text: String = "",
        val description: String = "",
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0,
    )

    data class Track(val title: String, val artist: String)

    fun read(packageName: String, nodes: List<Node>): Track? {
        if (packageName != "ru.kinopoisk.tv" || nodes.none { it.text.trim() == "Сейчас играет" }) return null
        // The cover's accessible name repeats the title below it. This distinguishes
        // actual track metadata from the header, playlist name and playback controls.
        for (cover in nodes) {
            val name = cover.description.trim()
            if (name.isEmpty() || cover.right - cover.left < 100 || cover.bottom - cover.top < 100) continue
            val title = nodes.firstOrNull {
                it.text.trim() == name && it.top >= cover.bottom &&
                    it.left < cover.right && it.right > cover.left
            } ?: continue
            val artist = nodes.filter {
                it.text.isNotBlank() && it.text.trim() != name && it.top >= title.bottom &&
                    it.left < title.right && it.right > title.left &&
                    it.top - title.bottom < (cover.bottom - cover.top) / 3
            }.minByOrNull { it.top }?.text?.trim().orEmpty()
            return Track(name, artist)
        }
        return null
    }
}
