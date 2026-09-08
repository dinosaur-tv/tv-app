package com.dinotv.app.domain

data class NowPlayingTrack(
    val title: String,
    val artist: String,
    val source: String,
    val isPlaying: Boolean,
    val volumePercent: Int? = null,
    val artworkUrl: String = "",
    val deviceName: String = "Телевизор",
) {
    fun toJson(): String = buildString {
        append('{')
        append("\"title\":").append(quote(title))
        append(",\"artist\":").append(quote(artist))
        append(",\"source\":").append(quote(source))
        append(",\"isPlaying\":").append(isPlaying)
        if (volumePercent != null) append(",\"volumePercent\":").append(volumePercent)
        append(",\"artworkUrl\":").append(quote(artworkUrl))
        append(",\"deviceName\":").append(quote(deviceName))
        append('}')
    }

    fun toReportJson(): String = copy(
        artworkUrl = if (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://")) artworkUrl else "",
    ).toJson()

    companion object {
        fun quote(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}

object NowPlayingMeta {
    fun sourceFor(packageName: String): String = when {
        packageName.contains("kinopoisk") || packageName.contains("yandex") -> "Яндекс Музыка"
        else -> "Музыка"
    }

    fun from(
        packageName: String,
        title: String?,
        artist: String?,
        album: String?,
        playing: Boolean,
        volume: Int? = null,
        volumeMax: Int? = null,
        artworkUrl: String = "",
        allowUntitled: Boolean = false,
    ): NowPlayingTrack? {
        var name = title?.trim().orEmpty()
        if (name.isEmpty()) {
            if (!allowUntitled) return null
            name = sourceFor(packageName)
        }
        val who = artist?.trim().orEmpty().ifBlank { album?.trim().orEmpty() }
        // `volume` may already be a 0–100 percent (volumeMax=100) or a raw stream level.
        val percent = if (volume != null && volumeMax != null && volumeMax > 0) {
            (100 * volume / volumeMax).coerceIn(0, 100)
        } else {
            null
        }
        return NowPlayingTrack(
            title = name,
            artist = who,
            source = sourceFor(packageName),
            isPlaying = playing,
            volumePercent = percent,
            artworkUrl = artworkUrl,
        )
    }
}
