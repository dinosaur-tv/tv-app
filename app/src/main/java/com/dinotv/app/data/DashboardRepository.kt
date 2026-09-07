package com.dinotv.app.data

import com.dinotv.app.domain.CalendarEvent
import com.dinotv.app.domain.DashboardMode
import com.dinotv.app.domain.DashboardState
import com.dinotv.app.domain.DayAgenda
import com.dinotv.app.domain.DisplayTheme
import com.dinotv.app.domain.Weather
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The screen consumes one aggregate model. A production implementation fetches this
 * from the private Dino TV service, where Google tokens and Telegram webhooks live.
 * No third-party credential should ever be embedded in an APK.
 */
interface DashboardRepository {
    fun dashboard(now: LocalDateTime, mode: DashboardMode, theme: DisplayTheme): DashboardState
}

class DemoDashboardRepository : DashboardRepository {
    override fun dashboard(now: LocalDateTime, mode: DashboardMode, theme: DisplayTheme): DashboardState {
        val today = now.toLocalDate()
        fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(day, LocalTime.of(hour, minute))
        val warmGold = 0xFFD1A466
        val moss = 0xFF8EA77A
        val rose = 0xFFD18182
        val blue = 0xFF87A9C7
        val days = (0..30).map { day ->
            val date = today.plusDays(day.toLong())
            val events = when (day) {
                0 -> listOf(
                    CalendarEvent("standup", "План на день", at(date, 10), at(date, 10, 30), "Миша", warmGold),
                    CalendarEvent("walk", "Прогулка в парке", at(date, 18, 30), at(date, 20), "Наташа", moss),
                    CalendarEvent("dinner", "Ужин дома", at(date, 20, 30), at(date, 22), "Наташа", rose),
                )
                1 -> listOf(
                    CalendarEvent("focus", "Фокус-работа", at(date, 11), at(date, 13), "Миша", warmGold),
                    CalendarEvent("yoga", "Йога", at(date, 19), at(date, 20), "Наташа", blue),
                )
                2 -> listOf(CalendarEvent("groceries", "Купить продукты", at(date, 18), at(date, 19), "Наташа", moss))
                4 -> listOf(CalendarEvent("cinema", "Кино", at(date, 20), at(date, 22, 30), "Миша", rose))
                6 -> listOf(CalendarEvent("family", "Завтрак", at(date, 11), at(date, 13), "Миша", moss))
                else -> emptyList()
            }
            DayAgenda(date, events)
        }
        return DashboardState(
            now = now,
            mode = mode,
            theme = theme,
            weather = Weather(14, 12, "Облачно с прояснениями", 16, 9, "Санкт-Петербург"),
            nowPlaying = null,
            agenda = days,
        )
    }
}
