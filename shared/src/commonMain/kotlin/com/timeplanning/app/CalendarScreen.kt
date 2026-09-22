package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private const val DAYS_AHEAD = 13

private fun Int.pad2() = toString().padStart(2, '0')

private data class DisplayEvent(val dateKey: LocalDate, val timeLabel: String, val isAllDay: Boolean, val event: CalendarEventInfo)

private fun toDisplayEvent(event: CalendarEventInfo): DisplayEvent {
    if (event.isAllDay) {
        return DisplayEvent(LocalDate.parse(event.start), "All day", true, event)
    }
    val tz = TimeZone.currentSystemDefault()
    val startLocal = Instant.parse(event.start).toLocalDateTime(tz)
    val endLocal = Instant.parse(event.end).toLocalDateTime(tz)
    val label = "${startLocal.hour.pad2()}:${startLocal.minute.pad2()}–${endLocal.hour.pad2()}:${endLocal.minute.pad2()}"
    return DisplayEvent(startLocal.date, label, false, event)
}

@Composable
fun CalendarScreen(apiClient: ApiClient, sessionToken: String, currentTab: BottomTab, onSelectTab: (BottomTab) -> Unit) {
    val scope = rememberCoroutineScope()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<CalendarEventInfo>>(emptyList()) }
    var calendars by remember { mutableStateOf<List<GoogleCalendarInfo>>(emptyList()) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savingSelection by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var selectedDate by remember { mutableStateOf(today) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val end = today.plus(DAYS_AHEAD, DateTimeUnit.DAY)
            val fetchedEvents = apiClient.fetchCalendarEvents(sessionToken, today.toString(), end.toString())
            val fetchedCalendars = apiClient.fetchCalendars(sessionToken)
            fetchedEvents to fetchedCalendars
        }.onSuccess { (fetchedEvents, fetchedCalendars) ->
            events = fetchedEvents
            calendars = fetchedCalendars
            pendingSelection = fetchedCalendars.filter { it.selected }.map { it.id }.toSet()
        }.onFailure {
            error = "Couldn't load calendar: ${it.message}"
        }
        loading = false
    }

    val displayEvents = remember(events) { events.map(::toDisplayEvent) }
    val dateRange = remember(today) { (0..DAYS_AHEAD).map { today.plus(it, DateTimeUnit.DAY) } }
    val dayEvents = remember(displayEvents, selectedDate) { displayEvents.filter { it.dateKey == selectedDate } }
    val allDayEvents = remember(dayEvents) { dayEvents.filter { it.isAllDay } }
    val timedEvents = remember(dayEvents) { dayEvents.filter { !it.isAllDay }.sortedBy { it.timeLabel } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BottomNavBar(current = currentTab, onSelect = onSelectTab) },
    ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { showCalendarPicker = !showCalendarPicker }) {
                Text(if (showCalendarPicker) "Hide calendars" else "Calendars")
            }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        if (showCalendarPicker) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Choose which calendars to show", style = MaterialTheme.typography.titleSmall)
                    calendars.forEach { calendar ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = calendar.id in pendingSelection,
                                onCheckedChange = { checked ->
                                    pendingSelection = if (checked) {
                                        pendingSelection + calendar.id
                                    } else {
                                        pendingSelection - calendar.id
                                    }
                                },
                            )
                            Text(calendar.summary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !savingSelection,
                            onClick = {
                                savingSelection = true
                                scope.launch {
                                    // Selecting every calendar is equivalent to the server's own
                                    // "empty selection" default — send [] so clearing back to "all"
                                    // later doesn't require a separate affordance.
                                    val idsToSend = if (pendingSelection.size == calendars.size) emptyList() else pendingSelection.toList()
                                    runCatching { apiClient.updateSelectedCalendars(sessionToken, idsToSend) }
                                        .onFailure { error = "Couldn't save calendar selection: ${it.message}" }
                                    savingSelection = false
                                    showCalendarPicker = false
                                    refreshKey++
                                }
                            },
                        ) { Text("Save") }
                        if (savingSelection) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(dateRange) { date ->
                DayChip(date = date, selected = date == selectedDate, isToday = date == today) { selectedDate = date }
            }
        }

        if (allDayEvents.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                allDayEvents.forEach { display -> AllDayEventCard(display) }
            }
        }

        when {
            !loading && dayEvents.isEmpty() -> {
                Text(
                    "Nothing on your calendars for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            timedEvents.isNotEmpty() -> {
                TimelineView(events = timedEvents.map { it.event }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            else -> Spacer(Modifier.weight(1f))
        }
    }
    }
}

@Composable
private fun DayChip(date: LocalDate, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val dayLabel = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = foreground)
        Text(
            date.day.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = foreground,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AllDayEventCard(display: DisplayEvent) {
    val accent = display.event.calendarSummary.hashToPaletteColor().toColorOrNull() ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("All day", style = MaterialTheme.typography.labelSmall, color = accent)
        Text(display.event.title, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Positions each timed event as a coloured block on an hour-ruled timeline,
 * offset/sized proportionally to its start time and duration — rather than
 * the old flat chronological list — so the day's shape is visible at a
 * glance. Colour is hashed from the calendar name (Google calendars have no
 * colour field the API exposes here), reusing the same palette/hash as
 * category colours for a consistent look.
 */
@Composable
private fun TimelineView(events: List<CalendarEventInfo>, modifier: Modifier = Modifier) {
    val tz = remember { TimeZone.currentSystemDefault() }
    data class TimedSlot(val event: CalendarEventInfo, val startMinutes: Int, val endMinutes: Int)

    val slots = remember(events) {
        events.map { e ->
            val start = Instant.parse(e.start).toLocalDateTime(tz)
            val end = Instant.parse(e.end).toLocalDateTime(tz)
            TimedSlot(e, start.hour * 60 + start.minute, end.hour * 60 + end.minute)
        }
    }

    val startHour = ((slots.minOfOrNull { it.startMinutes } ?: 8 * 60) / 60).coerceAtMost(8)
    val endHour = (((slots.maxOfOrNull { it.endMinutes } ?: 20 * 60) + 59) / 60).coerceAtLeast(20).coerceAtMost(24)
    val hourHeight = 64.dp
    val hourLabelWidth = 52.dp

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column {
            for (hour in startHour until endHour) {
                Row(modifier = Modifier.fillMaxWidth().height(hourHeight)) {
                    Text(
                        "${hour.pad2()}:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(hourLabelWidth).padding(top = 2.dp, end = 8.dp),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
        }

        slots.forEach { slot ->
            val clampedStart = slot.startMinutes.coerceAtLeast(startHour * 60)
            val durationMinutes = (slot.endMinutes - clampedStart).coerceAtLeast(20)
            val topOffset = hourHeight * ((clampedStart - startHour * 60) / 60f)
            val blockHeight = hourHeight * (durationMinutes / 60f)
            val accent = slot.event.calendarSummary.hashToPaletteColor().toColorOrNull() ?: MaterialTheme.colorScheme.primary
            val label = "${(clampedStart / 60).pad2()}:${(clampedStart % 60).pad2()}"

            Column(
                modifier = Modifier
                    .padding(start = hourLabelWidth + 8.dp, end = 8.dp)
                    .offset(y = topOffset)
                    .height(blockHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    slot.event.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    "$label · ${slot.event.calendarSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}
