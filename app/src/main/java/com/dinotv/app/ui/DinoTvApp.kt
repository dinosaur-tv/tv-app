package com.dinotv.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dinotv.app.domain.CalendarEvent
import com.dinotv.app.domain.DashboardMode
import com.dinotv.app.domain.DashboardState
import com.dinotv.app.domain.DayAgenda
import com.dinotv.app.domain.DisplayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Russian = Locale("ru")

private data class DinoPalette(
    val background: Color,
    val panel: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val line: Color,
    val ambient: Color,
    val selected: Color,
)

private fun paletteFor(theme: DisplayTheme) = when (theme) {
    DisplayTheme.FOREST -> DinoPalette(Color(0xFF111A15), Color(0xCC1B2921), Color(0xFFF2EEE6), Color(0xFFB9B8AE), Color(0xFFD1A466), Color(0xFF445147), Color(0x334C7053), Color(0xFF24362A))
    DisplayTheme.STONE -> DinoPalette(Color(0xFF1B1D20), Color(0xD925282C), Color(0xFFF1F2F3), Color(0xFFB8BDC2), Color(0xFFAEB7C1), Color(0xFF4A4F55), Color(0x333C454F), Color(0xFF30353A))
    DisplayTheme.TOBACCO -> DinoPalette(Color(0xFF2A201A), Color(0xD9382C24), Color(0xFFF5EADD), Color(0xFFCAB9A8), Color(0xFFD5A36B), Color(0xFF5D4939), Color(0x334D3021), Color(0xFF47352A))
    DisplayTheme.TAUPE -> DinoPalette(Color(0xFF282421), Color(0xD938322D), Color(0xFFF1EDE7), Color(0xFFC2BAB1), Color(0xFFBAA18B), Color(0xFF584E46), Color(0x332F2925), Color(0xFF403932))
    DisplayTheme.APPLE -> DinoPalette(Color(0xFFF5F5F7), Color(0xF2FFFFFF), Color(0xFF1D1D1F), Color(0xFF6E6E73), Color(0xFF0071E3), Color(0xFFD2D2D7), Color(0x1A0071E3), Color(0xFFE8F2FF))
}

private val LocalDinoPalette = androidx.compose.runtime.staticCompositionLocalOf { paletteFor(DisplayTheme.FOREST) }
private val Ink: Color @Composable get() = LocalDinoPalette.current.ink
private val MutedInk: Color @Composable get() = LocalDinoPalette.current.muted
private val Forest: Color @Composable get() = LocalDinoPalette.current.background
private val Line: Color @Composable get() = LocalDinoPalette.current.line
private val Gold: Color @Composable get() = LocalDinoPalette.current.accent
private val Card: Color @Composable get() = LocalDinoPalette.current.panel

@Composable
fun DinoTvApp(viewModel: DinoTvViewModel) {
    val state = viewModel.state
    DinoTheme(state.theme) {
        val pixelShiftTransition = rememberInfiniteTransition(label = "OLED pixel shift")
        val pixelShift by pixelShiftTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 90_000), RepeatMode.Reverse),
            label = "subtle display shift",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Forest),
        ) {
            AmbientBackground()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = pixelShift.dp, y = (-pixelShift).dp)
                    .padding(horizontal = 54.dp, vertical = 34.dp),
            ) {
                Header(state, viewModel::selectMode, viewModel::selectTheme)
                Spacer(Modifier.height(30.dp))
                AnimatedContent(targetState = state.mode, label = "dashboard mode") { mode ->
                    when (mode) {
                        DashboardMode.NOW -> NowScreen(state, viewModel::toggleMusic, viewModel::nextTrack, viewModel::changeVolume)
                        DashboardMode.TODAY -> TodayScreen(state)
                        DashboardMode.WEEK -> WeekScreen(state)
                        DashboardMode.MONTH -> MonthScreen(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun DinoTheme(theme: DisplayTheme, content: @Composable () -> Unit) {
    val palette = paletteFor(theme)
    CompositionLocalProvider(LocalDinoPalette provides palette) {
        MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = palette.accent,
            onPrimary = palette.background,
            surface = palette.panel,
            onSurface = palette.ink,
            outline = palette.line,
        ),
        content = content,
        )
    }
}

@Composable
private fun AmbientBackground() {
    val palette = LocalDinoPalette.current
    val transition = rememberInfiniteTransition(label = "ambient glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 32_000), RepeatMode.Reverse),
        label = "ambient drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val glowCenterX = size.width * (.72f + phase * .16f)
        val glowCenterY = size.height * (.10f + phase * .08f)
        drawCircle(
            brush = Brush.radialGradient(listOf(palette.ambient, Color.Transparent)),
            radius = size.minDimension * (.70f + phase * .10f),
            center = Offset(glowCenterX, glowCenterY),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x223A5340), Color.Transparent)),
            radius = size.minDimension * .58f,
            center = Offset(size.width * (.16f + phase * .06f), size.height * .9f),
        )
        drawCircle(
            color = Color(0x228DAA81),
            radius = size.minDimension * .32f,
            center = Offset(size.width * (.86f + phase * .05f), size.height * (.14f + phase * .04f)),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun Header(state: DashboardState, onMode: (DashboardMode) -> Unit, onTheme: (DisplayTheme) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            DashboardMode.entries.forEach { mode ->
                ModeButton(mode, state.mode == mode) { onMode(mode) }
            }
        }
        ThemePicker(state.theme, onTheme)
    }
}

@Composable
private fun ModeButton(mode: DashboardMode, selected: Boolean, onClick: () -> Unit) {
    var focused = false
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tab focus")
    Text(
        text = mode.label.uppercase(Russian),
        color = if (selected || focused) Ink else MutedInk,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) LocalDinoPalette.current.selected.copy(alpha = .45f) else Color.Transparent)
            .border(if (focused) 1.dp else 0.dp, if (focused) Gold else Color.Transparent, RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = (16 * scale).dp, vertical = (9 * scale).dp),
    )
}

@Composable
private fun ThemePicker(selected: DisplayTheme, onTheme: (DisplayTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ТЕМА", color = MutedInk, fontSize = 9.sp, letterSpacing = 1.2.sp)
        DisplayTheme.entries.forEach { theme ->
            val focusedColor = paletteFor(theme).accent
            var focused = false
            Box(
                modifier = Modifier
                    .size(if (theme == selected) 18.dp else 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(focusedColor)
                    .border(if (focused) 2.dp else if (theme == selected) 1.dp else 0.dp, Ink, RoundedCornerShape(12.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onTheme(theme) }
                    .focusable(),
            )
        }
    }
}

@Composable
private fun NowScreen(
    state: DashboardState,
    onToggleMusic: () -> Unit,
    onNextTrack: () -> Unit,
    onVolumeChange: (Int) -> Unit,
) {
    val next = state.agenda.flatMap { it.events }.firstOrNull { it.end.isAfter(state.now) }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(modifier = Modifier.weight(1.12f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    state.now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Russian)).replaceFirstChar { it.uppercase() },
                    color = MutedInk, fontSize = 17.sp, letterSpacing = 1.1.sp,
                )
                Text(state.now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Ink, fontSize = 104.sp, fontFamily = FontFamily.Serif, lineHeight = 104.sp)
                Text("Ваш дом. Ваш ритм.", color = Gold, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                WeatherPanel(state)
                if (state.nowPlaying == null) MusicConnectPrompt() else MusicPanel(state, onToggleMusic, onNextTrack, onVolumeChange)
            }
        }
        Column(modifier = Modifier.weight(.88f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            NextEventPanel(next, state, Modifier.weight(1f))
            TodayList(state.agenda.first(), Modifier.weight(1.18f))
        }
    }
}

@Composable
private fun WeatherPanel(state: DashboardState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("☁", fontSize = 40.sp, color = Gold)
        Spacer(Modifier.width(16.dp))
        Text("${state.weather.temperature}°", fontSize = 48.sp, color = Ink, fontFamily = FontFamily.Serif)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(state.weather.description, fontSize = 15.sp, color = Ink)
            Text("${state.weather.location} · ощущается как ${state.weather.feelsLike}°", fontSize = 12.sp, color = MutedInk)
        }
    }
}

@Composable
private fun MusicPanel(
    state: DashboardState,
    onToggleMusic: () -> Unit,
    onNextTrack: () -> Unit,
    onVolumeChange: (Int) -> Unit,
) {
    val playing = state.nowPlaying ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Card.copy(alpha = .75f))
            .border(1.dp, Line.copy(alpha = .4f), RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("СЕЙЧАС ИГРАЕТ", color = Gold, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Text("${playing.title} · ${playing.artist}", color = Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MusicControl("−") { onVolumeChange(-1) }
        MusicControl(if (playing.isPlaying) "Ⅱ" else "▶", onToggleMusic)
        MusicControl("›") { onNextTrack() }
        MusicControl("+") { onVolumeChange(1) }
        Text("${playing.volumePercent}%", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun MusicControl(symbol: String, action: () -> Unit) {
    var focused = false
    Text(
        symbol,
        color = if (focused) Ink else Gold,
        fontSize = 17.sp,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(27.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Gold else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable { action() }
            .focusable(),
    )
}

@Composable
private fun MusicConnectPrompt() {
    val context = LocalContext.current
    Text(
        text = "ПОДКЛЮЧИТЬ ЯНДЕКС МУЗЫКУ",
        color = Gold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Line.copy(alpha = .7f), RoundedCornerShape(14.dp))
            .clickable {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .focusable()
            .padding(horizontal = 15.dp, vertical = 11.dp),
    )
}

@Composable
private fun NextEventPanel(event: CalendarEvent?, state: DashboardState, modifier: Modifier) {
    DinoCard(modifier) {
        Text("ДАЛЬШЕ", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(20.dp))
        if (event == null) {
            Text("Свободный вечер", color = Ink, fontSize = 28.sp, fontFamily = FontFamily.Serif)
            Text("В календаре больше ничего нет.", color = MutedInk, fontSize = 14.sp)
        } else {
            Text(event.title, color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text("${event.start.format(timeFormat())} — ${event.end.format(timeFormat())} · ${event.calendarName}", color = MutedInk, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            val mins = java.time.Duration.between(state.now, event.start).toMinutes().coerceAtLeast(0)
            Text(if (mins == 0L) "Сейчас" else "Через $mins мин", color = Gold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TodayList(day: DayAgenda, modifier: Modifier) {
    DinoCard(modifier) {
        Text("СЕГОДНЯ", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(13.dp))
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            day.events.forEach { EventRow(it) }
        }
    }
}

@Composable
private fun TodayScreen(state: DashboardState) {
    Column(Modifier.fillMaxSize()) {
        Title("Сегодня", state.now.toLocalDate())
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DinoCard(Modifier.weight(1.1f).fillMaxHeight()) {
                Text("РАСПИСАНИЕ", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(20.dp))
                if (state.agenda.first().events.isEmpty()) Text("Сегодня можно никуда не спешить.", color = MutedInk)
                else Column(verticalArrangement = Arrangement.spacedBy(19.dp)) { state.agenda.first().events.forEach { EventRow(it, large = true) } }
            }
            Column(Modifier.weight(.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DinoCard(Modifier.weight(1f)) {
                    Text("ПОГОДА", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("${state.weather.temperature}°", fontSize = 55.sp, color = Ink, fontFamily = FontFamily.Serif)
                    Text("${state.weather.description}, ${state.weather.high}° / ${state.weather.low}°", color = MutedInk, fontSize = 14.sp)
                }
                DinoCard(Modifier.weight(1f)) {
                    Text("НАПОМИНАНИЕ", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Время для вас двоих", color = Ink, fontFamily = FontFamily.Serif, fontSize = 24.sp)
                    Text("Сегодня вечером оставьте телефон в другой комнате.", color = MutedInk, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun WeekScreen(state: DashboardState) {
    Column(Modifier.fillMaxSize()) {
        Title("Неделя", state.now.toLocalDate())
        Spacer(Modifier.height(24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(state.agenda.take(7)) { day ->
                WeekDay(day, day.date == state.now.toLocalDate(), Modifier.width(205.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun WeekDay(day: DayAgenda, isToday: Boolean, modifier: Modifier = Modifier) {
    DinoCard(modifier, if (isToday) Color(0xDD24362A) else Card) {
        Text(day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Russian).uppercase(Russian), color = if (isToday) Gold else MutedInk, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(day.date.format(DateTimeFormatter.ofPattern("d MMM", Russian)), color = Ink, fontSize = 25.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(23.dp))
        if (day.events.isEmpty()) Text("Свободно", color = MutedInk, fontSize = 13.sp)
        else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { day.events.forEach { EventRow(it) } }
    }
}

@Composable
private fun MonthScreen(state: DashboardState) {
    val today = state.now.toLocalDate()
    val first = today.withDayOfMonth(1)
    val leading = first.dayOfWeek.value - 1
    val cells = (0 until 42).map { first.minusDays(leading.toLong()).plusDays(it.toLong()) }
    Column(Modifier.fillMaxSize()) {
        Title(today.month.getDisplayName(TextStyle.FULL, Russian).replaceFirstChar { it.uppercase() }, today)
        Spacer(Modifier.height(19.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { label ->
                Text(label, color = MutedInk, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    week.forEach { date -> MonthCell(date, today, state.agenda.firstOrNull { it.date == date }?.events.orEmpty(), Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(date: LocalDate, today: LocalDate, events: List<CalendarEvent>, modifier: Modifier) {
    val currentMonth = date.month == today.month
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (date == today) LocalDinoPalette.current.selected else Card.copy(alpha = .55f))
            .border(if (date == today) 1.dp else 0.dp, Gold, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Column {
            Text(date.dayOfMonth.toString(), color = if (currentMonth) Ink else Color(0x665A655D), fontSize = 17.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(6.dp))
            events.take(2).forEach { event ->
                Text(event.title, color = Color(event.color), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, large: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(if (large) 9.dp else 7.dp).clip(RoundedCornerShape(9.dp)).background(Color(event.color)))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, color = Ink, fontSize = if (large) 21.sp else 15.sp, fontFamily = if (large) FontFamily.Serif else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${event.start.format(timeFormat())} — ${event.end.format(timeFormat())} · ${event.calendarName}", color = MutedInk, fontSize = if (large) 13.sp else 11.sp)
        }
    }
}

@Composable
private fun DinoCard(modifier: Modifier = Modifier, color: Color = Card, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = color,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line.copy(alpha = .55f)),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(25.dp), content = content)
    }
}

@Composable
private fun Title(title: String, date: LocalDate) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, color = Ink, fontSize = 52.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.width(16.dp))
        Text(date.format(DateTimeFormatter.ofPattern("yyyy", Russian)), color = Gold, fontSize = 15.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 10.dp))
    }
}

private fun timeFormat(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
