package com.dinotv.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class DashboardMode(val label: String) {
    NOW("Сейчас"), TODAY("Сегодня"), WEEK("Неделя")
}

enum class DisplayTheme(val id: String, val label: String) {
    GALLERY("gallery", "Галерея"),
    HOME_DAY("home-day", "Дом · День"),
    HOME_EVENING("home-evening", "Дом · Вечер"),
    PALACE("palace", "Дворец"),
    OAK_STUDY("oak-study", "Дубовый кабинет"),
    NIGHT("night", "Ночь"),
    PLAY("play", "Шалость"),
    FOREST("forest", "Лес"),
    MOUNTAINS("mountains", "Горы"),
    SEA("sea", "Море"),
    SPACE("space", "Космос"),
    PETERSBURG("petersburg", "Петербург"),
    ROME("rome", "Рим"),
    FLORENCE("florence", "Флоренция"),
    VENICE("venice", "Венеция"),
    RUS("rus", "Русский узор"),
    BYZANTIUM("byzantium", "Византия"),
    INDIA("india", "Индия"),
    ITALY("italy", "Итальянский узор");

    companion object {
        fun fromId(id: String): DisplayTheme? = entries.firstOrNull { it.id == id }
    }
}

data class Weather(
    val temperature: Int,
    val feelsLike: Int,
    val description: String,
    val high: Int,
    val low: Int,
    val location: String,
)

data class NowPlaying(
    val title: String,
    val artist: String,
    val source: String,
    val isPlaying: Boolean,
    val volumePercent: Int,
)

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val calendarName: String,
    val color: Long,
    val allDay: Boolean = false,
)

data class DayAgenda(val date: LocalDate, val events: List<CalendarEvent>)

data class DashboardState(
    val now: LocalDateTime,
    val mode: DashboardMode,
    val theme: DisplayTheme,
    val weather: Weather,
    val nowPlaying: NowPlaying?,
    val agenda: List<DayAgenda>,
    val isDemo: Boolean = true,
)

object TvPower {
    fun shouldApply(appliedAt: String, incomingAt: String): Boolean =
        incomingAt.isNotEmpty() && incomingAt != appliedAt
}
