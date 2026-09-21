package com.timeplanning.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
fun CalendarScreen(apiClient: ApiClient, sessionToken: String, onBack: () -> Unit) {
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

    val grouped = remember(events) {
        events.map(::toDisplayEvent)
            .groupBy { it.dateKey }
            .toList()
            .sortedBy { (date, _) -> date }
    }

    Column(modifier = Modifier.safeContentPadding().fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = { showCalendarPicker = !showCalendarPicker }) {
                    Text(if (showCalendarPicker) "Hide calendars" else "Calendars")
                }
                TextButton(onClick = onBack) { Text("Back") }
            }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
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

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!loading && grouped.isEmpty()) {
                item { Text("Nothing on your calendars for the next $DAYS_AHEAD days.") }
            }
            grouped.forEach { (date, dayEvents) ->
                item {
                    Text(
                        if (date == today) "Today — $date" else date.toString(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(dayEvents.sortedWith(compareBy({ !it.isAllDay }, { it.timeLabel }))) { display ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(display.event.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${display.timeLabel} · ${display.event.calendarSummary}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
