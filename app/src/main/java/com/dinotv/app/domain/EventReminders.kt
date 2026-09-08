package com.dinotv.app.domain

data class IncomingEvent(
    val id: String,
    val title: String,
    val start: String,
    val allDay: Boolean,
    val owner: String,
)

data class EventCue(
    val id: String,
    val title: String,
    val owner: String,
    val start: String,
    val minutes: Int,
)

object EventReminders {
    const val LEAD_MINUTES = 5

    fun next(
        events: List<IncomingEvent>,
        nowMs: Long,
        shownIds: Set<String>,
        privacy: Boolean,
        showCalendar: Boolean,
        leadMinutes: Int = LEAD_MINUTES,
    ): EventCue? {
        if (privacy || !showCalendar) return null
        val upcoming = events
            .filter { event ->
                !event.allDay && event.id.isNotBlank() && event.start.isNotBlank() && startMs(event.start) > nowMs
            }
            .sortedBy { it.start }
        for (event in upcoming) {
            if (event.id in shownIds) continue
            val minutes = ((startMs(event.start) - nowMs + 30_000) / 60_000).toInt().coerceAtLeast(1)
            if (minutes > leadMinutes) return null
            return EventCue(
                id = event.id,
                title = event.title.ifBlank { "Дело" },
                owner = event.owner,
                start = event.start,
                minutes = minutes,
            )
        }
        return null
    }

    fun minutesPhrase(minutes: Int): String {
        val n = minutes.coerceAtLeast(1)
        val mod10 = n % 10
        val mod100 = n % 100
        val word = when {
            mod10 == 1 && mod100 != 11 -> "минуту"
            mod10 in 2..4 && mod100 !in 12..14 -> "минуты"
            else -> "минут"
        }
        return "Через $n $word"
    }

    private fun startMs(start: String): Long =
        try {
            java.time.Instant.parse(start).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(start).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(start).atOffset(java.time.ZoneOffset.ofHours(3)).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    0L
                }
            }
        }
}
