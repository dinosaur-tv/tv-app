package com.dinotv.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventRemindersTest {
    @Test
    fun `ignores distant guest and all-day events`() {
        val nowMs = java.time.OffsetDateTime.parse("2026-09-08T16:00:00+03:00").toInstant().toEpochMilli()
        val soon = IncomingEvent("1", "Ужин", "2026-09-08T16:05:00+03:00", false, "Наташа")
        assertNull(EventReminders.next(listOf(soon.copy(start = "2026-09-08T18:00:00+03:00")), nowMs, emptySet(), false, true))
        assertNull(EventReminders.next(listOf(soon), nowMs, emptySet(), true, true))
        assertNull(EventReminders.next(listOf(soon), nowMs, emptySet(), false, false))
        assertNull(EventReminders.next(listOf(soon.copy(allDay = true)), nowMs, emptySet(), false, true))
    }

    @Test
    fun `cues thirty minutes and again at five`() {
        val nowMs = java.time.OffsetDateTime.parse("2026-09-08T16:00:00+03:00").toInstant().toEpochMilli()
        val halfHour = EventReminders.next(
            listOf(IncomingEvent("meet", "Созвон", "2026-09-08T16:25:00+03:00", false, "Наташа")),
            nowMs,
            emptySet(),
            false,
            true,
        )
        assertEquals("meet@30", halfHour?.id)
        assertEquals(25, halfHour?.minutes)

        val five = EventReminders.next(
            listOf(IncomingEvent("meet", "Созвон", "2026-09-08T16:05:00+03:00", false, "Наташа")),
            nowMs,
            setOf("meet@30"),
            false,
            true,
        )
        assertEquals("meet@5", five?.id)
        assertEquals(5, five?.minutes)
        assertEquals("Через 5 минут", EventReminders.minutesPhrase(5))
        assertEquals("Через 1 минуту", EventReminders.minutesPhrase(1))
        assertEquals("Через 2 минуты", EventReminders.minutesPhrase(2))
    }

    @Test
    fun `skips a lead that already had its overlay`() {
        val nowMs = java.time.OffsetDateTime.parse("2026-09-08T16:00:00+03:00").toInstant().toEpochMilli()
        val events = listOf(
            IncomingEvent("soon", "Созвон", "2026-09-08T16:04:00+03:00", false, ""),
            IncomingEvent("next", "Ужин", "2026-09-08T16:05:00+03:00", false, ""),
        )
        assertEquals("next@5", EventReminders.next(events, nowMs, setOf("soon@5"), false, true)?.id)
    }
}
