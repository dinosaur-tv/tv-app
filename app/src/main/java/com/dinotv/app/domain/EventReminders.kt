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
    val LEADS = listOf(30, 5)

    fun next(
        events: List<IncomingEvent>,
        nowMs: Long,
        shownIds: Set<String>,
        privacy: Boolean,
        showCalendar: Boolean,
        leads: List<Int> = LEADS,
    ): EventCue? {
        if (privacy || !showCalendar) return null
        val upcoming = events
            .filter { event ->
                !event.allDay && event.id.isNotBlank() && event.start.isNotBlank() && startMs(event.start) > nowMs
            }
            .sortedBy { startMs(it.start) }
        for (event in upcoming) {
            val minutes = ((startMs(event.start) - nowMs + 30_000) / 60_000).toInt().coerceAtLeast(1)
            for (lead in leads) {
                if (!inBand(minutes, lead)) continue
                val cueId = "${event.id}@$lead"
                if (cueId in shownIds) continue
                return EventCue(
                    id = cueId,
                    title = event.title.ifBlank { "Дело" },
                    owner = event.owner,
                    start = event.start,
                    minutes = minutes,
                )
            }
        }
        return null
    }

    fun inBand(minutes: Int, lead: Int): Boolean = when (lead) {
        30 -> minutes in 6..30
        5 -> minutes in 1..5
        else -> minutes in 1..lead
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
