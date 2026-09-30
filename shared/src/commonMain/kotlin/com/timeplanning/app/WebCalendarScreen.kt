package com.timeplanning.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private val HourHeight = 78.dp
private val HourLabelWidth = 72.dp
/** Secondary text on the web calendar/Today headers — the muted plum-grey of the earlier palette. */
internal val PaleDay = Color(0xFF85767E)
/** Other days' date numbers (today's is full ink). */
private val OtherDay = Color(0xFF6E5F67)
/** Every block on a day that's already gone by, whatever it was — so what's still ahead stands out. */
internal val PastBlock = Color(0xFFEDEAE9)
internal val PastText = Color(0xFFA59CA0)
private val DetailCardWidth = 420.dp
/** A day never gets narrower than this — below it, the week scrolls sideways instead. */
private val MinDayWidth = 190.dp
/** Other days' dates and names in the reference: a pale beige that barely stands off the page. */
private val PaleBeige = Color(0xFFDCD2CA)

/** "8:30 AM" — the reference design's 12-hour times. */
internal fun clock12(minutes: Int): String {
    val h = (minutes / 60) % 24
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${(minutes % 60).pad2()} ${if (h < 12) "AM" else "PM"}"
}

internal fun range12(start: Int, end: Int): String = "${clock12(start)} – ${clock12(end)}"

/** "08 AM" — the grid's hour labels. */
internal fun hourLabel12(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "${h12.pad2()} ${if (hour % 24 < 12) "AM" else "PM"}"
}

/**
 * Desktop web's calendar, after the reference design: a breadcrumb header with plain icon controls
 * and one dark primary button (re-plan), huge day numbers with a tick under each (today's in the
 * accent with a dot), a square-cornered pastel week grid with faint 12-hour labels and a now line,
 * and a detail card beside a clicked block — where a planned block's tasks can be ticked off.
 * Same data and block grouping as the phone's CalendarScreen (groupingAdjacentPlanBlocks).
 */
@Composable
fun WebCalendarScreen(
    apiClient: ApiClient,
    sessionToken: String,
    /** Phone layout: a two-row header, two days on screen (swipe for the rest), details at the bottom. */
    compact: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(tz) }
    val thisMonday = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val nextMonday = thisMonday.plus(7, DateTimeUnit.DAY)

    var weekStart by remember { mutableStateOf(thisMonday) }
    /** Phone only: the one day shown under the week strip. */
    var selectedDay by remember { mutableStateOf(today) }
    var refreshKey by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<CalendarEventInfo>>(emptyList()) }
    var planBlocks by remember { mutableStateOf<List<ScheduledBlockResponse>>(emptyList()) }
    var commitments by remember { mutableStateOf<List<CommitmentBlockResponse>>(emptyList()) }
    var clashes by remember { mutableStateOf<List<CalendarClash>>(emptyList()) }
    var allDayPrompts by remember { mutableStateOf<List<AllDayPrompt>>(emptyList()) }
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var calendars by remember { mutableStateOf<List<GoogleCalendarInfo>>(emptyList()) }
    var pendingSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var savingSelection by remember { mutableStateOf(false) }
    var planning by remember { mutableStateOf(false) }
    var showReplanMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showTasks by remember { mutableStateOf(true) }
    var showCommitments by remember { mutableStateOf(true) }
    var showEvents by remember { mutableStateOf(true) }
    /** "taskId|date" for everything ticked off this week, so a block's tasks show as done. */
    var doneKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var completingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var openItem by remember { mutableStateOf<DisplayItem?>(null) }
    var nowMinutes by remember { mutableStateOf(Clock.System.now().toLocalDateTime(tz).let { it.hour * 60 + it.minute }) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMinutes = Clock.System.now().toLocalDateTime(tz).let { it.hour * 60 + it.minute }
        }
    }

    LaunchedEffect(refreshKey, weekStart) {
        loading = true
        error = null
        openItem = null
        runCatching {
            val fetchedEvents = apiClient.fetchCalendarEvents(sessionToken, weekStart.toString(), weekStart.plus(6, DateTimeUnit.DAY).toString())
            val fetchedCalendars = apiClient.fetchCalendars(sessionToken)
            val plan = runCatching { apiClient.fetchPlanBlocks(sessionToken, weekStart.toString()) }.getOrNull()
            Triple(fetchedEvents, fetchedCalendars, plan)
        }.onSuccess { (fetchedEvents, fetchedCalendars, plan) ->
            events = fetchedEvents
            calendars = fetchedCalendars
            pendingSelection = fetchedCalendars.filter { it.selected }.map { it.id }.toSet()
            planBlocks = plan?.blocks.orEmpty()
            commitments = plan?.commitments.orEmpty()
        }.onFailure { error = "Couldn't load calendar: ${it.message}" }
        doneKeys = runCatching { apiClient.fetchWeekTasks(sessionToken) }.getOrDefault(emptyList())
            .flatMap { t -> t.completedDates.map { "${t.taskId}|$it" } }.toSet()
        clashes = runCatching { apiClient.fetchClashes(sessionToken) }.getOrDefault(emptyList())
        allDayPrompts = runCatching { apiClient.fetchAllDayPrompts(sessionToken) }.getOrDefault(emptyList())
        loading = false
    }

    val weekOffset = ((weekStart.toEpochDays() - thisMonday.toEpochDays()) / 7).toInt()

    fun replan(target: String) {
        showReplanMenu = false
        planning = true
        scope.launch {
            runCatching {
                when (target) {
                    "today" -> apiClient.regenerateDay(sessionToken, today.toString())
                    "this" -> apiClient.generatePlan(sessionToken, thisMonday.toString())
                    else -> apiClient.generatePlan(sessionToken, nextMonday.toString())
                }
            }.onSuccess {
                weekStart = if (target == "next") nextMonday else thisMonday
                refreshKey++
            }.onFailure { error = "Couldn't re-plan: ${it.serverMessage()}" }
            planning = false
        }
    }

    fun complete(taskId: Long, date: LocalDate) {
        completingIds = completingIds + taskId
        scope.launch {
            runCatching { apiClient.completeTask(sessionToken, taskId) }
                .onSuccess { doneKeys = doneKeys + "$taskId|$date" }
                .onFailure { error = "Couldn't tick that off: ${it.serverMessage()}" }
            completingIds = completingIds - taskId
        }
    }

    fun resolveAllDay(prompt: AllDayPrompt, choice: AllDayChoice) {
        resolvingKey = prompt.seriesKey
        scope.launch {
            runCatching { apiClient.resolveAllDay(sessionToken, prompt, choice) }.onSuccess { refreshKey++ }
                .onFailure { error = "Couldn't save that: ${it.serverMessage()}" }
            resolvingKey = null
        }
    }

    fun resolveClash(clash: CalendarClash, action: ClashAction) {
        resolvingKey = clash.eventKey
        scope.launch {
            runCatching { apiClient.resolveClash(sessionToken, clash, action) }.onSuccess { refreshKey++ }
                .onFailure { error = "Couldn't sort out the clash with \"${clash.eventTitle}\": ${it.serverMessage()}" }
            resolvingKey = null
        }
    }

    val days = remember(weekStart) { (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) } }
    LaunchedEffect(weekStart) {
        // Moving weeks keeps the same weekday selected (or today, in the current week).
        if (selectedDay !in days) selectedDay = if (today in days) today else weekStart.plus(selectedDay.dayOfWeek.ordinal, DateTimeUnit.DAY)
    }
    val displayItems = remember(events, planBlocks, commitments) {
        (events.flatMap { it.toDisplayItems() } + planBlocks.map { it.toDisplayItem() } + commitments.map { it.toDisplayItem() })
            .groupingAdjacentPlanBlocks()
    }.filter { item ->
        when (item.kind) {
            TimelineItemKind.PLAN_BLOCK -> showTasks
            TimelineItemKind.COMMITMENT -> showCommitments
            TimelineItemKind.CALENDAR_EVENT -> showEvents
        }
    }
    val timedItems = displayItems.filter { !it.isAllDay }
    val allDayItems = displayItems.filter { it.isAllDay }
    // A multi-day event's middle/last day starts at midnight — don't let that drag the whole grid back to 00:00.
    val startHour = ((timedItems.filter { it.startMinutes > 0 }.minOfOrNull { it.startMinutes } ?: (8 * 60)) / 60).coerceAtMost(7)
    val endHour = ((timedItems.maxOfOrNull { it.endMinutes } ?: (20 * 60)) + 59).div(60).coerceIn(21, 24)
    val weekClashes = clashes.filter { c -> days.any { it.toString() == c.date } }
    val weekPrompts = allDayPrompts.filter { p -> p.firstDate <= days.last().toString() && days.first().toString() <= p.lastDate }
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sidePad = if (compact) 16.dp else 48.dp
    val endPad = if (compact) 16.dp else 32.dp
    val labelW = if (compact) 64.dp else HourLabelWidth

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (compact) {
            // Phone: title + re-plan on one row, week navigation + view controls on the next.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Calendar", fontSize = 19.sp, color = ink)
                    Text(monthLabel(days.first(), days.last()), fontSize = 14.sp, color = PaleDay, maxLines = 1)
                }
                Box {
                    DarkButton(label = "Re-plan", busy = planning, onClick = { showReplanMenu = true })
                    DropdownMenu(expanded = showReplanMenu, onDismissRequest = { showReplanMenu = false }, containerColor = Color.White, shape = RectangleShape) {
                        ReplanMenuItem("Re-plan today", "Re-plan the rest of today only") { replan("today") }
                        ReplanMenuItem("Re-plan this week", "Re-plan ${rangeLabel(thisMonday, thisMonday.plus(6, DateTimeUnit.DAY))}") { replan("this") }
                        ReplanMenuItem("Re-plan next week", "Re-plan ${rangeLabel(nextMonday, nextMonday.plus(6, DateTimeUnit.DAY))}") { replan("next") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainIconButton(onClick = { weekStart = weekStart.minus(7, DateTimeUnit.DAY) }) {
                    WizardIconGlyph(WizardGlyph.CHEVRON, ink, Modifier.size(18.dp).rotate(90f))
                }
                Text(
                    "Today",
                    fontSize = 14.sp,
                    color = if (weekOffset == 0) PaleDay else ink,
                    modifier = Modifier.clickable(enabled = weekOffset != 0) { weekStart = thisMonday }.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                PlainIconButton(onClick = { weekStart = weekStart.plus(7, DateTimeUnit.DAY) }) {
                    WizardIconGlyph(WizardGlyph.CHEVRON, ink, Modifier.size(18.dp).rotate(-90f))
                }
                Box(Modifier.weight(1f))
                PlainIconButton(onClick = { showCalendarPicker = !showCalendarPicker }) {
                    WizardIconGlyph(WizardGlyph.CALENDAR, ink, Modifier.size(22.dp))
                }
                Box {
                    PlainIconButton(onClick = { showFilterMenu = true }) { SlidersIcon(ink, Modifier.size(22.dp)) }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, containerColor = Color.White, shape = RectangleShape) {
                        Text("Show on calendar", fontSize = 12.sp, color = muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        FilterMenuItem("Tasks", showTasks) { showTasks = it }
                        FilterMenuItem("Commitments", showCommitments) { showCommitments = it }
                        FilterMenuItem("Calendar events", showEvents) { showEvents = it }
                    }
                }
            }
        } else {
            // Header: breadcrumb on the left; plain icon controls and one dark primary button on the right.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 30.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Calendar", style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = ink)
                WizardIconGlyph(WizardGlyph.CHEVRON, PaleDay, Modifier.size(12.dp).rotate(-90f))
                Text(monthLabel(days.first(), days.last()), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = PaleDay)
                Box(Modifier.weight(1f))
                PlainIconButton(onClick = { weekStart = weekStart.minus(7, DateTimeUnit.DAY) }) {
                    WizardIconGlyph(WizardGlyph.CHEVRON, ink, Modifier.size(18.dp).rotate(90f))
                }
                Text(
                    "Today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (weekOffset == 0) PaleDay else ink,
                    modifier = Modifier.clickable(enabled = weekOffset != 0) { weekStart = thisMonday }.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                PlainIconButton(onClick = { weekStart = weekStart.plus(7, DateTimeUnit.DAY) }) {
                    WizardIconGlyph(WizardGlyph.CHEVRON, ink, Modifier.size(18.dp).rotate(-90f))
                }
                Box(Modifier.width(12.dp))
                PlainIconButton(onClick = { showCalendarPicker = !showCalendarPicker }) {
                    WizardIconGlyph(WizardGlyph.CALENDAR, ink, Modifier.size(24.dp))
                }
                Box {
                    PlainIconButton(onClick = { showFilterMenu = true }) { SlidersIcon(ink, Modifier.size(24.dp)) }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, containerColor = Color.White, shape = RectangleShape) {
                        Text("Show on calendar", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        FilterMenuItem("Tasks", showTasks) { showTasks = it }
                        FilterMenuItem("Commitments", showCommitments) { showCommitments = it }
                        FilterMenuItem("Calendar events", showEvents) { showEvents = it }
                    }
                }
                Box(Modifier.width(16.dp))
                Box {
                    DarkButton(label = "Re-plan", busy = planning, onClick = { showReplanMenu = true })
                    DropdownMenu(expanded = showReplanMenu, onDismissRequest = { showReplanMenu = false }, containerColor = Color.White, shape = RectangleShape) {
                        ReplanMenuItem("Re-plan today", "Re-plan the rest of today only") { replan("today") }
                        ReplanMenuItem("Re-plan this week", "Re-plan ${rangeLabel(thisMonday, thisMonday.plus(6, DateTimeUnit.DAY))}") { replan("this") }
                        ReplanMenuItem("Re-plan next week", "Re-plan ${rangeLabel(nextMonday, nextMonday.plus(6, DateTimeUnit.DAY))}") { replan("next") }
                    }
                }
            }

        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = sidePad)) }

        if (showCalendarPicker) {
            CalendarPickerPanel(
                calendars = calendars,
                selected = pendingSelection,
                saving = savingSelection,
                onToggle = { id, checked -> pendingSelection = if (checked) pendingSelection + id else pendingSelection - id },
                onSave = {
                    savingSelection = true
                    scope.launch {
                        // Every calendar selected == the server's own "empty selection" default.
                        val ids = if (pendingSelection.size == calendars.size) emptyList() else pendingSelection.toList()
                        runCatching { apiClient.updateSelectedCalendars(sessionToken, ids) }
                            .onFailure { error = "Couldn't save calendar selection: ${it.message}" }
                        savingSelection = false
                        showCalendarPicker = false
                        refreshKey++
                    }
                },
            )
        }

        if (weekClashes.isNotEmpty() || weekPrompts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = sidePad, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                weekPrompts.forEach { AllDayPromptCard(it, resolvingKey == it.seriesKey, { c -> resolveAllDay(it, c) }, Modifier.width(if (compact) 300.dp else 360.dp)) }
                weekClashes.forEach { ClashCard(it, resolvingKey == it.eventKey, { a -> resolveClash(it, a) }, Modifier.width(if (compact) 300.dp else 360.dp)) }
            }
        }

        // Each day gets at least MinDayWidth, so a narrow window scrolls sideways instead of squashing
        // the week. The dates and the grid share one horizontal scroll; the hour labels stay pinned.
        val hScroll = rememberScrollState()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Phone: the week strip picks one day, shown full width. Wider screens show the whole
            // week, each day at least MinDayWidth (scrolling sideways below that).
            val gridDays = if (compact) listOf(selectedDay) else days
            val columnWidth = if (compact) maxWidth - sidePad - labelW - endPad else maxOf((maxWidth - sidePad - labelW - endPad) / 7, MinDayWidth)
            val gridWidth = columnWidth * gridDays.size
            val hours = (startHour until endHour).toList()
            val gridHeight = HourHeight * hours.size
            val showNow = today in gridDays && nowMinutes in (startHour * 60) until (endHour * 60)
            val nowY = HourHeight * ((nowMinutes - startHour * 60) / 60f)
            val vScroll = rememberScrollState()
            if (compact) {
                // Open today scrolled to an hour before now; other days from the top.
                val density = androidx.compose.ui.platform.LocalDensity.current
                LaunchedEffect(selectedDay) {
                    val target = if (selectedDay == today) (nowMinutes - 60 - startHour * 60).coerceAtLeast(0) else 0
                    vScroll.scrollTo(with(density) { (HourHeight * (target / 60f)).toPx() }.toInt())
                }
            }
            Column(Modifier.fillMaxSize()) {
                if (compact) {
                    PhoneWeekStrip(
                        days = days,
                        today = today,
                        selected = selectedDay,
                        daysWithPlans = timedItems.map { it.dateKey }.toSet() + allDayItems.map { it.dateKey },
                        onSelect = { selectedDay = it },
                        onSwipe = { forward -> weekStart = weekStart.plus(if (forward) 7 else -7, DateTimeUnit.DAY) },
                    )
                    val allDay = allDayItems.filter { it.dateKey == selectedDay }
                    if (allDay.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(start = sidePad + labelW, end = endPad, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            allDay.forEach { item ->
                                Text("All day · ${item.title}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(item.blockColor(past = selectedDay < today)).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(start = sidePad + labelW, end = endPad).horizontalScroll(hScroll)) {
                        WebDayHeaderRow(days, today, allDayItems, columnWidth)
                    }
                }

                if (loading && displayItems.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WebAccent) }
                }

                Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll).padding(start = sidePad, end = endPad, top = 16.dp, bottom = 24.dp)) {
                    // Pinned hour labels (and the now tag).
                    Box(Modifier.width(labelW).height(gridHeight)) {
                        hours.forEachIndexed { i, hour ->
                            Text(hourLabel12(hour), fontSize = 12.sp, color = PaleDay, modifier = Modifier.offset(y = HourHeight * i - 10.dp))
                        }
                        if (showNow) {
                            Text(
                                clock12(nowMinutes).replace(" ", ""),
                                fontSize = if (compact) 11.sp else 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.offset(x = (-6).dp, y = nowY - 11.dp).background(WebAccent).padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        Box(Modifier.width(gridWidth).height(gridHeight)) {
                            hours.indices.forEach { i ->
                                Box(Modifier.offset(y = HourHeight * i).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            }
                            gridDays.forEachIndexed { dayIndex, date ->
                                val dayItems = timedItems.filter { it.dateKey == date }
                                val lanes = overlapLanes(dayItems, { it.startMinutes }, { it.endMinutes })
                                dayItems.forEach { item ->
                                    val (lane, laneCount) = lanes.getValue(item)
                                    val laneWidth = (columnWidth - 8.dp) / laneCount
                                    // Clipped to the grid's first hour, so an all-night event just starts at the top.
                                    val visibleStart = maxOf(item.startMinutes, startHour * 60)
                                    if (item.endMinutes <= visibleStart) return@forEach
                                    val height = HourHeight * ((item.endMinutes - visibleStart).coerceAtLeast(10) / 60f)
                                    WeekBlock(
                                        item = item,
                                        // Phone greys today's finished blocks too (like Today); web greys whole past days.
                                        past = date < today || (compact && date == today && item.endMinutes <= nowMinutes),
                                        compact = height < StackedBlockMinHeight,
                                        onClick = { openItem = item },
                                        modifier = Modifier
                                            .offset(x = columnWidth * dayIndex + 4.dp + laneWidth * lane, y = HourHeight * ((visibleStart - startHour * 60) / 60f))
                                            .width(laneWidth - 2.dp)
                                            .height(height - 2.dp),
                                    )
                                }
                            }
                            if (showNow) {
                                Box(Modifier.offset(y = nowY).fillMaxWidth().height(1.5.dp).background(WebAccent))
                            }
                            // The clicked block's detail card, beside its column (flipped left near the right edge).
                            if (!compact) openItem?.let { item ->
                                val dayIndex = days.indexOf(item.dateKey).coerceAtLeast(0)
                                val x = if (dayIndex < 4) columnWidth * (dayIndex + 1) + 8.dp else columnWidth * dayIndex - DetailCardWidth - 8.dp
                                val y = HourHeight * ((maxOf(item.startMinutes, startHour * 60) - startHour * 60) / 60f)
                                val tasks = item.taskIds.zip(item.taskNames).map { (id, name) -> DetailTask(id, name, "$id|${item.dateKey}" in doneKeys) }
                                WebDetailCard(
                                    title = item.title,
                                    color = item.blockColor(),
                                    timeLabel = range12(item.startMinutes, item.endMinutes),
                                    dateLabel = shortDateLabel(item.dateKey),
                                    tasks = tasks,
                                    description = when (item.kind) {
                                        TimelineItemKind.CALENDAR_EVENT -> "From your ${item.subtitle} calendar."
                                        TimelineItemKind.COMMITMENT -> "A fixed commitment from your Plan settings."
                                        TimelineItemKind.PLAN_BLOCK -> null
                                    },
                                    completingIds = completingIds,
                                    onTick = { complete(it, item.dateKey) },
                                    onClose = { openItem = null },
                                    modifier = Modifier.offset(x = x.coerceAtLeast(0.dp), y = y),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    // Phone: the tapped block's details as a card along the bottom of the screen.
    if (compact) openItem?.let { item ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            WebDetailCard(
                title = item.title,
                color = item.blockColor(),
                timeLabel = range12(item.startMinutes, item.endMinutes),
                dateLabel = shortDateLabel(item.dateKey),
                tasks = item.taskIds.zip(item.taskNames).map { (id, name) -> DetailTask(id, name, "$id|${item.dateKey}" in doneKeys) },
                description = when (item.kind) {
                    TimelineItemKind.CALENDAR_EVENT -> "From your ${item.subtitle} calendar."
                    TimelineItemKind.COMMITMENT -> "A fixed commitment from your Plan settings."
                    TimelineItemKind.PLAN_BLOCK -> null
                },
                completingIds = completingIds,
                onTick = { complete(it, item.dateKey) },
                onClose = { openItem = null },
                floating = false,
                modifier = Modifier.shadow(16.dp, RectangleShape),
            )
        }
    }
}

/** Commitments warm beige, calendar events pink, tasks a pastel of their category's colour. Light grey once the day has passed. */
private fun DisplayItem.blockColor(past: Boolean = false): Color = if (past) PastBlock else when (kind) {
    TimelineItemKind.COMMITMENT -> WebCommitmentColor
    TimelineItemKind.CALENDAR_EVENT -> WebCalendarEventColor
    TimelineItemKind.PLAN_BLOCK -> (colorHex.toColorOrNull() ?: Color(0xFFB9AFC0)).webPastel()
}

/** One size for each kind of text in every calendar block, whatever the block's height — only the layout changes. */
internal val BlockTitleSize = 13.sp
internal val BlockTimeSize = 11.sp
internal val BlockDetailSize = 11.sp
/** Below this height a block can't fit time + title + detail stacked, so it uses one line. */
internal val StackedBlockMinHeight = 66.dp

@Composable
private fun WeekBlock(item: DisplayItem, past: Boolean, compact: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val titleColor = if (past) PastText else MaterialTheme.colorScheme.onSurface
    val detailColor = if (past) PastText else MaterialTheme.colorScheme.onSurfaceVariant
    val detail = when {
        item.taskNames.size > 1 -> "${item.taskNames.size} tasks"
        item.taskNames.size == 1 && item.taskNames.first() != item.title -> item.taskNames.first()
        item.kind == TimelineItemKind.CALENDAR_EVENT -> item.subtitle
        item.kind == TimelineItemKind.COMMITMENT -> "Fixed"
        else -> null
    }
    if (compact) {
        Row(
            modifier = modifier.background(item.blockColor(past)).clickable(onClick = onClick).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.title, fontSize = BlockTitleSize, fontWeight = if (item.kind == TimelineItemKind.CALENDAR_EVENT) FontWeight.Normal else FontWeight.Bold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(clock12(item.startMinutes), fontSize = BlockTimeSize, color = detailColor, maxLines = 1)
        }
        return
    }
    Column(
        modifier = modifier.background(item.blockColor(past)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(range12(item.startMinutes, item.endMinutes), fontSize = BlockTimeSize, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            InfoIcon(detailColor)
        }
        Text(item.title, fontSize = BlockTitleSize, fontWeight = if (item.kind == TimelineItemKind.CALENDAR_EVENT) FontWeight.Normal else FontWeight.Bold, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, fontSize = BlockDetailSize, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A task line in a detail card. */
internal data class DetailTask(val id: Long, val name: String, val done: Boolean)

/**
 * The reference design's detail card, shown beside a clicked block: title and close, time and date, then either the
 * block's tasks — tickable — or a short description, and one full-width square action. Shared by the
 * web Calendar and Today.
 */
@Composable
internal fun WebDetailCard(
    title: String,
    color: Color,
    timeLabel: String,
    dateLabel: String,
    tasks: List<DetailTask>,
    description: String?,
    completingIds: Set<Long>,
    onTick: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** false = sits in a side column (full width, bordered) rather than floating beside a block. */
    floating: Boolean = true,
    /** When set, clicking a task's name opens that task. */
    onOpenTask: ((Long) -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val frame = if (floating) modifier.width(DetailCardWidth).shadow(18.dp, RectangleShape) else modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(frame.background(Color.White).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(32.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                WizardIconGlyph(WizardGlyph.CLOSE, ink, Modifier.size(16.dp))
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WizardIconGlyph(WizardGlyph.CLOCK, muted, Modifier.size(20.dp))
            Text(timeLabel, style = MaterialTheme.typography.bodyMedium, color = muted)
            Box(Modifier.width(22.dp))
            WizardIconGlyph(WizardGlyph.CALENDAR, muted, Modifier.size(20.dp))
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        Text(if (tasks.isNotEmpty()) "Tasks" else "Description", style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (tasks.isNotEmpty()) {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tasks.forEach { task ->
                    val completing = task.id in completingIds
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (task.done) WebAccent else Color.Transparent)
                                .border(1.5.dp, if (task.done) WebAccent else muted, CircleShape)
                                .clickable(enabled = !task.done && !completing) { onTick(task.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                completing -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = WebAccent)
                                task.done -> WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(11.dp))
                            }
                        }
                        Text(
                            task.name,
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (task.done) TextDecoration.LineThrough else null),
                            color = if (task.done) muted else ink,
                            modifier = if (onOpenTask != null) Modifier.clickable { onOpenTask(task.id) } else Modifier,
                        )
                    }
                }
            }
        } else {
            Text(description.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        Box(
            Modifier.padding(top = 22.dp).fillMaxWidth().height(58.dp).background(WebAccent).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("Close", style = MaterialTheme.typography.bodyLarge, color = Color.White) }
    }
}

/** The small outlined "i" in a block's top corner. */
@Composable
internal fun InfoIcon(color: Color) {
    Box(Modifier.size(18.dp).border(1.2.dp, color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Text("i", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun PlainIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** The reference design's one dark, square primary button (its account switcher), used here for re-planning. */
@Composable
internal fun DarkButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.background(WebAccent).clickable(enabled = !busy, onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        else WizardIconGlyph(WizardGlyph.REPEAT, Color.White, Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        WizardIconGlyph(WizardGlyph.CHEVRON, Color.White, Modifier.size(14.dp))
    }
}

@Composable
internal fun ReplanMenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(270.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterMenuItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.width(220.dp).clickable { onChange(!checked) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Three horizontal sliders — the "view options" glyph (not in WizardGlyph). */
@Composable
private fun SlidersIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.width * 0.08f
        listOf(0.22f to 0.68f, 0.5f to 0.32f, 0.78f to 0.6f).forEach { (y, knob) ->
            val cy = size.height * y
            drawLine(color, Offset(0f, cy), Offset(size.width, cy), strokeWidth = stroke)
            drawCircle(Color.White, radius = size.width * 0.11f, center = Offset(size.width * knob, cy))
            drawCircle(color, radius = size.width * 0.11f, center = Offset(size.width * knob, cy), style = Stroke(stroke))
        }
    }
}

@Composable
private fun CalendarPickerPanel(
    calendars: List<GoogleCalendarInfo>,
    selected: Set<String>,
    saving: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 48.dp, vertical = 4.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(16.dp),
    ) {
        Text("Show these calendars", style = MaterialTheme.typography.titleSmall)
        calendars.forEach { calendar ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = calendar.id in selected, onCheckedChange = { onToggle(calendar.id, it) })
                Text(calendar.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSave, enabled = !saving) { Text("Save") }
            if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * Side-by-side columns only where items genuinely overlap in time — each item gets (its column, how
 * many columns its own overlap group needs), so a day with no overlaps is one full-width column.
 * Unlike the phone timeline's assignLanes this ignores any minimum display height, so back-to-back
 * short tasks stay in one column. Shared with WebTodayScreen.
 */
internal fun <T> overlapLanes(items: List<T>, start: (T) -> Int, end: (T) -> Int): Map<T, Pair<Int, Int>> {
    val result = mutableMapOf<T, Pair<Int, Int>>()
    val sorted = items.sortedWith(compareBy({ start(it) }, { -end(it) }))
    var group = mutableListOf<T>()
    var groupEnd = -1
    fun flush() {
        val laneEnds = mutableListOf<Int>()
        val laneOf = group.associateWith { item ->
            val free = laneEnds.indexOfFirst { it <= start(item) }
            if (free >= 0) { laneEnds[free] = end(item); free } else { laneEnds += end(item); laneEnds.lastIndex }
        }
        laneOf.forEach { (item, lane) -> result[item] = lane to laneEnds.size.coerceAtLeast(1) }
        group = mutableListOf()
    }
    for (item in sorted) {
        if (group.isNotEmpty() && start(item) >= groupEnd) flush()
        group += item
        groupEnd = maxOf(groupEnd, end(item))
    }
    if (group.isNotEmpty()) flush()
    return result
}

private fun monthName(d: LocalDate) = d.month.name.lowercase().replaceFirstChar { it.uppercase() }

/** "September 2026", or "Sep – Oct 2026" for a week spanning two months — the breadcrumb's faded half. */
private fun monthLabel(first: LocalDate, last: LocalDate): String =
    if (first.month == last.month) "${monthName(first)} ${first.year}" else "${monthName(first).take(3)} – ${monthName(last).take(3)} ${last.year}"

internal fun rangeLabel(first: LocalDate, last: LocalDate): String =
    "${monthName(first).take(3)} ${first.day} – ${monthName(last).take(3)} ${last.day}, ${last.year}"

/** "Tue, Sep 29" — the detail card's date. */
internal fun shortDateLabel(date: LocalDate): String =
    "${date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, ${monthName(date).take(3)} ${date.day}"

/** The week's date header, after the reference: extra-heavy numbers (other days a pale beige, today dark), the day name, and a tick under each — today's a short tick with a small square dot. Fixed-width columns so it lines up with the (horizontally scrolling) grid. */
@Composable
internal fun WebDayHeaderRow(days: List<LocalDate>, today: LocalDate, allDayItems: List<DisplayItem>, columnWidth: Dp) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(top = 10.dp)) {
        days.forEach { date ->
            val isToday = date == today
            val color = if (isToday) ink else PaleBeige
            Column(Modifier.width(columnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                HeavyNumber(date.day.toString(), color)
                Text(
                    date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3),
                    fontSize = 14.sp,
                    color = color,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (isToday) {
                    Box(Modifier.padding(top = 10.dp).width(1.5.dp).height(10.dp).background(ink))
                    Box(Modifier.padding(top = 3.dp).size(5.dp).background(WebAccent))
                    Box(Modifier.height(2.dp))
                } else {
                    Box(Modifier.padding(top = 10.dp).width(1.5.dp).height(18.dp).background(ink.copy(alpha = 0.8f)))
                    Box(Modifier.height(2.dp))
                }
                allDayItems.filter { it.dateKey == date }.forEach { item ->
                    Text(
                        item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (date < today) PastText else ink,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp).fillMaxWidth()
                            .background(item.blockColor(past = date < today)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * An extra-heavy date number. The app's built-in font has no Black weight, so the bold glyph is
 * drawn a second time as an outline in the same colour, thickening every stroke to the reference's
 * chunky look without shipping a font file.
 */
@Composable
private fun HeavyNumber(text: String, color: Color) {
    val base = androidx.compose.ui.text.TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, lineHeight = 42.sp, color = color)
    Box {
        Text(text, style = base)
        Text(text, style = base.copy(drawStyle = Stroke(width = 3.5f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
    }
}

/**
 * Phone calendar's week strip: seven days in a row (the heavy date style, smaller), today in full ink
 * with an accent dot, the selected day on a soft mauve fill, a small bar under days with plans. Tap a
 * day to show it; swipe the strip sideways for the previous/next week.
 */
@Composable
private fun PhoneWeekStrip(
    days: List<LocalDate>,
    today: LocalDate,
    selected: LocalDate,
    daysWithPlans: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    var drag by remember { mutableStateOf(0f) }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (kotlin.math.abs(drag) > 80f) onSwipe(drag < 0); drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
        ) {
            days.forEach { date ->
                val isToday = date == today
                val color = if (isToday) ink else PaleBeige
                Column(
                    Modifier.weight(1f).background(if (date == selected) WebAccentSoft else Color.Transparent).clickable { onSelect(date) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        val base = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = color)
                        Text(date.day.toString(), style = base)
                        Text(date.day.toString(), style = base.copy(drawStyle = Stroke(width = 2f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
                    }
                    Text(date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = color)
                    Box(
                        Modifier.padding(top = 4.dp).width(if (isToday) 5.dp else 16.dp).height(if (isToday) 5.dp else 3.dp)
                            .background(if (isToday) WebAccent else if (date in daysWithPlans) PaleBeige else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}
