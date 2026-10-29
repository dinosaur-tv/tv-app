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
        if (packageName != "ru.kinopoisk.tv" || nodes.none { isHeader(it.text) }) return null
        // The cover's accessible name repeats the title below it. This distinguishes
        // actual track metadata from the header, playlist name and playback controls.
        for (cover in nodes) {
            val name = cover.description.trim()
            if (name.isEmpty() || cover.right - cover.left < 100 || cover.bottom - cover.top < 100) continue
            val title = nodes.firstOrNull {
                titleOf(it.text) == name && it.top >= cover.bottom &&
                    it.left < cover.right && it.right > cover.left
            } ?: continue
            val artist = nodes.filter {
                it.text.isNotBlank() && titleOf(it.text) != name && it.top >= title.bottom &&
                    it.left < title.right && it.right > title.left &&
                    it.top - title.bottom < (cover.bottom - cover.top) / 3
            }.minByOrNull { it.top }?.text?.trim().orEmpty()
            return Track(name, artist)
        }
        return null
    }

    /**
     * «Сейчас играет» over My Wave, but «Сейчас играет плейлист» (or «альбом») over the
     * rest. Matching only the first left every playlist unread; the TV then fell back to a
     * film's leftover session and the phone showed that film, paused.
     */
    private fun isHeader(text: String): Boolean = text.trim().startsWith(HEADER)

    private const val HEADER = "Сейчас играет"

    /**
     * An explicit track carries an «E» badge drawn inline after its title. Accessibility
     * reads it as a stray U+FFFD, so «7143 E» arrived as "7143 U+FFFD" and never matched
     * the cover: the phone then said nothing was playing.
     */
    private fun titleOf(text: String): String = text.trim().trimEnd(BADGE, OBJECT).trim()

    private val BADGE = Char(0xFFFD)
    private val OBJECT = Char(0xFFFC)
}
