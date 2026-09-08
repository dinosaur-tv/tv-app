package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventRemindersTest {
    private val now = 1_778_000_000_000L

    @Test
    fun `ignores distant guest and all-day events`() {
        val soon = IncomingEvent("1", "Ужин", "2026-09-08T16:05:00+03:00", false, "Наташа")
        assertNull(EventReminders.next(listOf(soon.copy(start = "2026-09-08T18:00:00+03:00")), now, emptySet(), false, true))
        assertNull(EventReminders.next(listOf(soon), now, emptySet(), true, true))
        assertNull(EventReminders.next(listOf(soon), now, emptySet(), false, false))
        assertNull(EventReminders.next(listOf(soon.copy(allDay = true)), now, emptySet(), false, true))
    }

    @Test
    fun `cues the next timed event inside five minutes`() {
        val nowMs = java.time.OffsetDateTime.parse("2026-09-08T16:00:00+03:00").toInstant().toEpochMilli()
        val cue = EventReminders.next(
            listOf(
                IncomingEvent("later", "Поздний", "2026-09-08T18:00:00+03:00", false, ""),
                IncomingEvent("soon", "Созвон", "2026-09-08T16:05:00+03:00", false, "Наташа"),
            ),
            nowMs,
            emptySet(),
            false,
            true,
        )
        assertEquals("soon", cue?.id)
        assertEquals("Созвон", cue?.title)
        assertEquals(5, cue?.minutes)
        assertEquals("Через 5 минут", EventReminders.minutesPhrase(5))
        assertEquals("Через 1 минуту", EventReminders.minutesPhrase(1))
        assertEquals("Через 2 минуты", EventReminders.minutesPhrase(2))
    }
}
