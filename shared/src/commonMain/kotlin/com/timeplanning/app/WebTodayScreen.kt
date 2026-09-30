package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private val DayHourHeight = 84.dp
private val DayHourLabelWidth = 64.dp

/** One thing on today's timeline — a plan block (with its tasks), a fixed commitment, or a calendar event. */
private data class DayItem(
    val title: String,
    val timeLabel: String,
    val tag: String?,
    val start: Int,
    val end: Int,
    val color: Color,
    val kind: TimelineItemKind,
    val subTasks: List<TodaySubTask>,
)

/**
 * Desktop web's Today, in the same visual language as WebCalendarScreen: the calendar's header
 * (title + one plum re-plan action), a single-day version of its hour grid with the same rounded
 * pastel blocks and now line — except a block lists its tasks with tick circles so they can be
 * ticked off in place — and a side column for what needs attention (all-day questions, clashes,
 * progress, done today, what's next). Blocks that have already finished go grey, like past days on the calendar.
 */
@Composable
fun WebTodayScreen(
    apiClient: ApiClient,
    sessionToken: String,
    onOpenTask: (Task, String?) -> Unit,
    /** Phone layout: header, then the cards, the timeline and Done today stacked in one scroll. */
    compact: Boolean = false,
    /** Phone only (web has the sidebar's New task): shows a "+" in the header. */
    onAddTask: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(tz) }
    val thisMonday = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }

    var refreshKey by remember { mutableStateOf(0) }
    /** The day being shown — today, or an earlier day to catch up on what was missed. */
    var viewDate by remember { mutableStateOf(today) }
    val isPast = viewDate < today
    /** On a past day: planned tasks that weren't done (and haven't been since). */
    var missed by remember { mutableStateOf<List<ReviewItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<DayItem>>(emptyList()) }
    var allTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var categoryColors by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var doneTodayIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var completingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var clashes by remember { mutableStateOf<List<CalendarClash>>(emptyList()) }
    var allDayPrompts by remember { mutableStateOf<List<AllDayPrompt>>(emptyList()) }
    var allDayTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    /** Everything completed today that wasn't on today's plan — from today's history. */
    var unplannedDone by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var replanning by remember { mutableStateOf(false) }
    var showReplanMenu by remember { mutableStateOf(false) }
    var openItem by remember { mutableStateOf<DayItem?>(null) }
    var upNextExpanded by remember { mutableStateOf(true) }
    var doneExpanded by remember { mutableStateOf(false) }
    /** Tomorrow's first planned block — what "Up next" shows once today's have all started. */
    var tomorrowFirst by remember { mutableStateOf<DayItem?>(null) }
    var nowMinutes by remember { mutableStateOf(Clock.System.now().toLocalDateTime(tz).let { it.hour * 60 + it.minute }) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMinutes = Clock.System.now().toLocalDateTime(tz).let { it.hour * 60 + it.minute }
        }
    }

    LaunchedEffect(refreshKey, viewDate) {
        loading = true
        error = null
        openItem = null
        if (viewDate < today) {
            // A past day: what was planned comes from its planned-vs-done history (a re-plan clears
            // past days' plan blocks, but the history keeps them), commitments from the week's plan.
            val d = viewDate
            val weekOf = d.minus(d.dayOfWeek.ordinal, DateTimeUnit.DAY)
            runCatching {
                val tasks = apiClient.fetchTasks(sessionToken)
                val categories = apiClient.fetchTaskCategories(sessionToken)
                val review = apiClient.fetchDayReview(sessionToken, d.toString())
                val plan = runCatching { apiClient.fetchPlanBlocks(sessionToken, weekOf.toString()) }.getOrNull()
                val events = runCatching { apiClient.fetchCalendarEvents(sessionToken, d.toString(), d.toString()) }.getOrDefault(emptyList())
                object {
                    val tasks = tasks; val categories = categories; val review = review; val plan = plan; val events = events
                }
            }.onSuccess { r ->
                allTasks = r.tasks
                categoryColors = r.categories.associate { it.id to it.displayColor }
                val reviewed = r.review.filter { it.taskId != null }
                doneTodayIds = reviewed.filter { it.outcome == "DONE" || it.caughtUp }.mapNotNull { it.taskId }.toSet()
                missed = reviewed.filter { it.outcome == "MISSED" && !it.caughtUp }
                val blocks = reviewed.map {
                    ScheduledBlockResponse(
                        taskId = it.taskId!!, taskName = it.taskName, categoryId = it.categoryId ?: 0L, categoryName = it.categoryName,
                        categoryColor = it.categoryColor, subcategoryName = it.subcategoryName, date = d.toString(), startTime = it.startTime, endTime = it.endTime,
                    )
                }
                val built = buildDayItems(blocks, r.plan?.commitments.orEmpty().filter { it.date == d.toString() }, r.events.flatMap { it.toDisplayItems() }.filter { it.dateKey == d })
                items = built.first
                allDayTitles = built.second
                tomorrowFirst = null
            }.onFailure { error = "Couldn't load that day: ${it.message}" }
            clashes = emptyList()
            allDayPrompts = emptyList()
            unplannedDone = runCatching { apiClient.fetchHistory(sessionToken, 1) }.getOrNull()
                ?.days?.firstOrNull { it.date == d.toString() }?.items?.filter { it.outcome == "UNPLANNED" }.orEmpty()
            loading = false
            return@LaunchedEffect
        }
        missed = emptyList()
        runCatching {
            val tasks = apiClient.fetchTasks(sessionToken)
            val categories = apiClient.fetchTaskCategories(sessionToken)
            val plan = apiClient.fetchPlanBlocks(sessionToken, thisMonday.toString())
            val events = runCatching { apiClient.fetchCalendarEvents(sessionToken, today.toString(), today.toString()) }.getOrDefault(emptyList())
            val week = runCatching { apiClient.fetchWeekTasks(sessionToken) }.getOrDefault(emptyList())
            object {
                val tasks = tasks; val categories = categories; val plan = plan; val events = events; val week = week
            }
        }.onSuccess { r ->
            allTasks = r.tasks
            categoryColors = r.categories.associate { it.id to it.displayColor }
            doneTodayIds = r.week.filter { today.toString() in it.completedDates }.map { it.taskId }.toSet() +
                r.tasks.filter { it.status == TaskStatus.COMPLETED }.map { it.id }
            val todayStr = today.toString()
            val tomorrowStr = today.plus(1, DateTimeUnit.DAY).toString()
            // Tomorrow may fall in next week's plan (today is a Sunday).
            val tomorrowPlan = if (today.dayOfWeek == kotlinx.datetime.DayOfWeek.SUNDAY) {
                runCatching { apiClient.fetchPlanBlocks(sessionToken, today.plus(1, DateTimeUnit.DAY).toString()) }.getOrNull()
            } else {
                r.plan
            }
            tomorrowFirst = tomorrowPlan?.let { plan ->
                val tBlocks = plan.blocks.filter { it.date == tomorrowStr }
                val tCommitments = plan.commitments.filter { it.date == tomorrowStr }
                (mergeBlocksIntoEntries(tBlocks, tCommitments) + commitmentsToEntries(tCommitments.excludingFilled(tBlocks, emptyList())))
                    .filter { e -> e.subTasks.any { it.taskId >= 0 } }
                    .minByOrNull { it.startMinutes }
                    ?.let { e ->
                        DayItem(
                            e.title, e.timeLabel, e.tag, e.startMinutes, e.endMinutes,
                            if (e.kind == TimelineItemKind.COMMITMENT) WebCommitmentColor else (e.colorHex.toColorOrNull() ?: Color.Gray).webPastel(),
                            e.kind, e.subTasks.filter { it.taskId >= 0 },
                        )
                    }
            }
            val blocks = r.plan.blocks.filter { it.date == todayStr }
            val commitments = r.plan.commitments.filter { it.date == todayStr }
            val eventItems = r.events.flatMap { it.toDisplayItems() }.filter { it.dateKey == today }
            allDayTitles = eventItems.filter { it.isAllDay }.map { it.title }
            val timedEvents = eventItems.filter { !it.isAllDay }
            val entries = mergeBlocksIntoEntries(blocks, commitments) + commitmentsToEntries(commitments.excludingFilled(blocks, timedEvents))
            items = entries.map { e ->
                DayItem(
                    title = e.title,
                    timeLabel = e.timeLabel,
                    tag = e.tag,
                    start = e.startMinutes,
                    end = e.endMinutes,
                    color = if (e.kind == TimelineItemKind.COMMITMENT) WebCommitmentColor else (e.colorHex.toColorOrNull() ?: Color.Gray).webPastel(),
                    kind = e.kind,
                    subTasks = e.subTasks.filter { it.taskId >= 0 },
                )
            } + timedEvents.map { ev ->
                DayItem(ev.title, ev.timeLabel, ev.subtitle, ev.startMinutes, ev.endMinutes, WebCalendarEventColor, TimelineItemKind.CALENDAR_EVENT, emptyList())
            }
        }.onFailure { error = "Couldn't load today: ${it.message}" }
        clashes = runCatching { apiClient.fetchClashes(sessionToken) }.getOrDefault(emptyList()).filter { it.date == today.toString() }
        unplannedDone = runCatching { apiClient.fetchHistory(sessionToken, 1) }.getOrNull()
            ?.days?.firstOrNull { it.date == today.toString() }?.items?.filter { it.outcome == "UNPLANNED" }.orEmpty()
        allDayPrompts = runCatching { apiClient.fetchAllDayPrompts(sessionToken) }.getOrDefault(emptyList())
            .filter { it.firstDate <= today.toString() && today.toString() <= it.lastDate }
        loading = false
    }

    fun replanToday() {
        showReplanMenu = false
        replanning = true
        scope.launch {
            runCatching { apiClient.regenerateDay(sessionToken, today.toString()) }
                .onSuccess { refreshKey++ }
                .onFailure { error = "Couldn't re-plan today: ${it.serverMessage()}" }
            replanning = false
        }
    }

    fun complete(taskId: Long) {
        completingIds = completingIds + taskId
        scope.launch {
            // On an earlier day, the tick counts for that day (not today).
            runCatching { apiClient.completeTask(sessionToken, taskId, if (viewDate < today) viewDate.toString() else null) }
                .onSuccess { doneTodayIds = doneTodayIds + taskId; refreshKey++ }
                .onFailure { error = "Couldn't tick that off: ${it.serverMessage()}" }
            completingIds = completingIds - taskId
        }
    }

    fun openTask(taskId: Long) {
        allTasks.find { it.id == taskId }?.let { onOpenTask(it, categoryColors[it.taskCategoryId]) }
    }

    fun resolveClash(clash: CalendarClash, action: ClashAction) {
        resolvingKey = clash.eventKey
        scope.launch {
            runCatching { apiClient.resolveClash(sessionToken, clash, action) }.onSuccess { refreshKey++ }
                .onFailure { error = "Couldn't sort out the clash: ${it.serverMessage()}" }
            resolvingKey = null
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

    val plannedTaskIds = items.flatMap { it.subTasks }.map { it.taskId }.distinct()
    val doneCount = plannedTaskIds.count { it in doneTodayIds }
    val startHour = ((items.filter { it.start > 0 }.minOfOrNull { it.start } ?: (8 * 60)) / 60).coerceAtMost(7)
    val endHour = ((items.maxOfOrNull { it.end } ?: (20 * 60)) + 59).div(60).coerceIn(21, 24)
    // Shown until it has finished — the current block stays "up next" until its end time passes.
    // Only blocks with tasks go in the side column — Wake up, Work, Tea and calendar events never do.
    val upNext = items.filter { it.subTasks.isNotEmpty() && it.end > nowMinutes }.minByOrNull { it.start }

    // The day's timeline (hour grid, blocks, now line) and the side cards, laid out side by side on
    // web and stacked on the phone (compact) — same pieces either way.
    val dayTimeline: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
                val hours = (startHour until endHour).toList()
                Column {
                    hours.forEach { hour ->
                        Row(Modifier.height(DayHourHeight).fillMaxWidth()) {
                            Text(hourLabel12(hour), fontSize = 12.sp, color = PaleDay, modifier = Modifier.width(DayHourLabelWidth).offset(y = (-10).dp))
                            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }
                if (allDayTitles.isNotEmpty()) {
                    Row(Modifier.padding(start = DayHourLabelWidth), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allDayTitles.forEach { title ->
                            Text("All day · $title", style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(WebCalendarEventColor).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                BoxWithConstraints(Modifier.padding(start = DayHourLabelWidth).fillMaxWidth().height(DayHourHeight * hours.size)) {
                    val lanes = overlapLanes(items, { it.start }, { it.end })
                    items.forEach { item ->
                        val (lane, laneCount) = lanes.getValue(item)
                        val laneWidth = maxWidth / laneCount
                        val visibleStart = maxOf(item.start, startHour * 60)
                        if (item.end <= visibleStart) return@forEach
                        val height = DayHourHeight * ((item.end - visibleStart).coerceAtLeast(10) / 60f)
                        DayBlock(
                            item = item,
                            past = isPast || item.end <= nowMinutes,
                            compact = height < StackedBlockMinHeight,
                            selected = openItem == item,
                            // Nothing to show on the right for a block without tasks, so it isn't selectable.
                            onClick = {
                                when {
                                    item.subTasks.isEmpty() -> Unit
                                    // Already Up next: just make sure its tasks are showing there.
                                    !isPast && item == upNext -> { openItem = null; upNextExpanded = true }
                                    else -> openItem = if (openItem == item) null else item
                                }
                            },
                            modifier = Modifier
                                .offset(x = laneWidth * lane, y = DayHourHeight * ((visibleStart - startHour * 60) / 60f))
                                .width(laneWidth - 4.dp)
                                .height(height - 3.dp),
                        )
                    }
                }
                if (!isPast && nowMinutes in (startHour * 60) until (endHour * 60)) {
                    val y = DayHourHeight * ((nowMinutes - startHour * 60) / 60f)
                    Box(Modifier.offset(y = y).padding(start = DayHourLabelWidth).fillMaxWidth().height(1.5.dp).background(WebAccent))
                    Text(
                        clock12(nowMinutes).replace(" ", ""),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.offset(x = (-6).dp, y = y - 13.dp).background(WebAccent).padding(horizontal = 7.dp, vertical = 5.dp),
                    )
                }
                if (!loading && items.isEmpty()) {
                    Text("Nothing planned for today yet — use the re-plan button to plan it.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = DayHourLabelWidth + 12.dp, top = 24.dp))
                }
        }
    }
    val missedCard: @Composable ColumnScope.() -> Unit = {
            if (isPast) {
                SideCard(if (missed.isEmpty()) "Missed" else "Missed · ${missed.size}") {
                    if (missed.isEmpty()) {
                        Text("Nothing missed — everything planned got done.", style = SidePrimary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Tick off anything you did but didn't mark.", style = SideSecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Grouped under each category (in the order they were planned that day).
                        missed.groupBy { it.categoryName }.forEach { (category, tasks) ->
                            val color = (tasks.first().categoryColor ?: category.hashToPaletteColor()).toColorOrNull() ?: Color.Gray
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).background(color))
                                Text(category, style = SidePrimary.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                                Text("${tasks.size}", style = SideSecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            tasks.forEach { m -> MissedLine(m, done = m.taskId in doneTodayIds, completing = m.taskId in completingIds, onTick = { m.taskId?.let(::complete) }, onOpen = { m.taskId?.let(::openTask) }) }
                        }
                    }
                }
            }
    }
    val sideCards: @Composable ColumnScope.() -> Unit = {
            missedCard()
            allDayPrompts.forEach { AllDayPromptCard(it, resolvingKey == it.seriesKey, { c -> resolveAllDay(it, c) }) }
            clashes.forEach { ClashCard(it, resolvingKey == it.eventKey, { a -> resolveClash(it, a) }) }
            SideCard("Progress") {
                val fraction = if (plannedTaskIds.isEmpty()) 0f else doneCount.toFloat() / plannedTaskIds.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (plannedTaskIds.isEmpty()) "No tasks planned today" else "$doneCount of ${plannedTaskIds.size} tasks done",
                        style = SidePrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (plannedTaskIds.isNotEmpty()) {
                        Text("${(fraction * 100).toInt()}%", style = SidePrimary.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(WebAccent))
                }
            }
            // The clicked block, in the Up next style, between Progress and Done today (click it again, or ✕, to clear).
            // Not repeated when it's the block already shown in Up next — that card shows it instead.
            openItem?.takeIf { it.subTasks.isNotEmpty() && !(!isPast && it == upNext) }?.let { item ->
                SelectedBlockCard(
                    item = item,
                    doneIds = doneTodayIds,
                    completingIds = completingIds,
                    onTick = ::complete,
                    onOpenTask = ::openTask,
                    onClose = { openItem = null },
                )
            }
            // Always shown on today: the current/next block, or — once today's have all finished — tomorrow's first.
            if (!isPast) {
            val next = upNext ?: tomorrowFirst
            // Tinted with the block's own category/subcategory colour (the same pastel as on the timeline).
            SideCard("Up next", background = next?.color ?: Color.White) {
                if (next == null) {
                    Text("Nothing else planned for today or tomorrow.", style = SidePrimary, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    val isTomorrow = upNext == null
                    val expandable = next.subTasks.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (expandable) Modifier.clickable { upNextExpanded = !upNextExpanded } else Modifier)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Name with its time on the right of the same line; the task count underneath.
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(next.title, style = SidePrimary, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(
                                    listOfNotNull(if (isTomorrow) "Tomorrow" else null, range12(next.start, next.end)).joinToString(" · "),
                                    style = SideSecondary,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                )
                            }
                            next.tag?.let {
                                Text(it, style = SideSecondary, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 1)
                            }
                        }
                        if (expandable) WizardIconGlyph(WizardGlyph.CHEVRON, MaterialTheme.colorScheme.onSurface, Modifier.size(14.dp).rotate(if (upNextExpanded) 0f else -90f))
                    }
                    if (expandable && upNextExpanded) {
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.08f)))
                        BlockTaskList(next, doneTodayIds, completingIds, ::complete, ::openTask, onTint = true)
                    }
                }
            }
            }
    }
    val doneCard: @Composable ColumnScope.() -> Unit = {
            val plannedDone = items.flatMap { it.subTasks }.distinctBy { it.taskId }.filter { it.taskId in doneTodayIds }
            // Today's history only marks a completion as planned once the day is over, so leave out
            // anything that's on today's plan — those are already counted via plannedDone.
            val plannedNames = plannedTaskIds.mapNotNull { id -> allTasks.find { it.id == id }?.name }.toSet()
            val extraDone = unplannedDone.filter { it.taskName !in plannedNames }.distinctBy { it.taskName }
            val doneTotal = plannedDone.size + extraDone.size
            SideCard(if (isPast) "Done that day" else "Done today") {
                Row(
                    modifier = Modifier.fillMaxWidth().then(if (doneTotal > 0) Modifier.clickable { doneExpanded = !doneExpanded } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (doneTotal) { 0 -> "Nothing ticked off yet."; 1 -> "1 task done"; else -> "$doneTotal tasks done" },
                        style = SidePrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (doneTotal > 0) {
                        WizardIconGlyph(WizardGlyph.CHEVRON, MaterialTheme.colorScheme.onSurface, Modifier.size(14.dp).rotate(if (doneExpanded) 0f else -90f))
                    }
                }
                if (doneExpanded && doneTotal > 0) {
                    Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    plannedDone.forEach { DoneLine(it.name, null) }
                    extraDone.forEach { DoneLine(it.taskName, "not planned today") }
                }
            }
    }

    if (compact) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isPast) "Earlier" else "Today",
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayArrow(left = true, enabled = true) { viewDate = viewDate.minus(1, DateTimeUnit.DAY) }
                        Text("${viewDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${viewDate.day} ${viewDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}", fontSize = 14.sp, color = if (isPast) MaterialTheme.colorScheme.onSurface else PaleDay, maxLines = 1)
                        DayArrow(left = false, enabled = isPast) { viewDate = viewDate.plus(1, DateTimeUnit.DAY) }
                        if (isPast) Text("Today", fontSize = 13.sp, color = WebAccent, modifier = Modifier.clickable { viewDate = today }.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
                onAddTask?.let { add ->
                    Box(Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant).background(Color.White).clickable(onClick = add), contentAlignment = Alignment.Center) {
                        WizardIconGlyph(WizardGlyph.PLUS, MaterialTheme.colorScheme.onSurface, Modifier.size(16.dp))
                    }
                    Box(Modifier.width(8.dp))
                }
                if (!isPast) Box {
                    DarkButton(label = "Re-plan", busy = replanning, onClick = { showReplanMenu = true })
                    DropdownMenu(expanded = showReplanMenu, onDismissRequest = { showReplanMenu = false }, containerColor = Color.White, shape = RectangleShape) {
                        ReplanMenuItem("Re-plan today", "Re-plan the rest of today only", ::replanToday)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Phone: the day as one list of blocks (option A) — tap a block to open its tasks in
                // place; the current one starts open. Finished blocks go grey but still open, so a
                // task done late can still be ticked.
                allDayPrompts.forEach { AllDayPromptCard(it, resolvingKey == it.seriesKey, { c -> resolveAllDay(it, c) }) }
                clashes.forEach { ClashCard(it, resolvingKey == it.eventKey, { a -> resolveClash(it, a) }) }
                missedCard()
                PhoneProgressLine(done = doneCount, total = plannedTaskIds.size)
                var expanded by remember(viewDate, items) { mutableStateOf(setOfNotNull(if (isPast) null else upNext)) }
                val sorted = items.sortedBy { it.start }
                val nowIndex = if (isPast) -1 else sorted.indexOfFirst { it.end > nowMinutes }
                if (!loading && sorted.isEmpty()) {
                    Text("Nothing planned for this day.", style = SidePrimary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                sorted.forEachIndexed { index, item ->
                    if (index == nowIndex) PhoneNowLine(nowMinutes)
                    PhoneDayRow(
                        item = item,
                        past = isPast || item.end <= nowMinutes,
                        expanded = item in expanded,
                        onToggle = { if (item.subTasks.isNotEmpty()) expanded = if (item in expanded) expanded - item else expanded + item },
                        doneIds = doneTodayIds,
                        completingIds = completingIds,
                        onTick = ::complete,
                        onOpenTask = ::openTask,
                    )
                }
                if (!isPast && nowIndex == -1 && sorted.isNotEmpty()) PhoneNowLine(nowMinutes)
                doneCard()
                Box(Modifier.height(12.dp))
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header — same shape as the calendar's: breadcrumb on the left, the dark re-plan button on the right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 30.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "Today" goes back to today when looking at an earlier day; the arrows step a day at a time.
            Text(
                "Today",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                color = if (isPast) WebAccent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(enabled = isPast) { viewDate = today },
            )
            WizardIconGlyph(WizardGlyph.CHEVRON, PaleDay, Modifier.size(12.dp).rotate(-90f))
            DayArrow(left = true, enabled = true) { viewDate = viewDate.minus(1, DateTimeUnit.DAY) }
            Text(longDateLabel(viewDate), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = if (isPast) MaterialTheme.colorScheme.onSurface else PaleDay)
            DayArrow(left = false, enabled = isPast) { viewDate = viewDate.plus(1, DateTimeUnit.DAY) }
            Box(Modifier.weight(1f))
            if (!isPast) Box {
                DarkButton(label = "Re-plan", busy = replanning, onClick = { showReplanMenu = true })
                DropdownMenu(expanded = showReplanMenu, onDismissRequest = { showReplanMenu = false }, containerColor = Color.White, shape = RectangleShape) {
                    ReplanMenuItem("Re-plan today", "Re-plan the rest of today only", ::replanToday)
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 48.dp)) }

        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            LaunchedEffect(startHour, items.isNotEmpty()) {
                // Open scrolled to an hour before now, rather than always at the top of the day.
                val target = with(density) { (DayHourHeight * ((nowMinutes - 60 - startHour * 60).coerceAtLeast(0) / 60f)).toPx() }
                scroll.scrollTo(target.toInt())
            }
            dayTimeline(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).padding(top = 12.dp))
            Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                sideCards()
                doneCard()
            }
        }
    }

}

/** Same look as the calendar's blocks (WebCalendarScreen.WeekBlock): time + ⓘ, bold category title, one detail line. */
@Composable
private fun DayBlock(item: DayItem, past: Boolean, compact: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val titleColor = if (past) PastText else MaterialTheme.colorScheme.onSurface
    val detailColor = if (past) PastText else MaterialTheme.colorScheme.onSurfaceVariant
    val titleWeight = if (item.kind == TimelineItemKind.CALENDAR_EVENT) FontWeight.Normal else FontWeight.Bold
    val detail = when {
        item.subTasks.size > 1 -> "${item.subTasks.size} tasks"
        item.subTasks.size == 1 && item.subTasks.first().name != item.title -> item.subTasks.first().name
        item.kind == TimelineItemKind.CALENDAR_EVENT -> item.tag
        item.kind == TimelineItemKind.COMMITMENT -> "Fixed"
        else -> null
    }
    val base = modifier
        .background(if (past) PastBlock else item.color)
        .then(if (selected) Modifier.border(2.dp, WebAccent) else Modifier)
        .clickable(onClick = onClick)
    if (compact) {
        Row(
            modifier = base.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.title, fontSize = BlockTitleSize, fontWeight = titleWeight, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(clock12(item.start), fontSize = BlockTimeSize, color = detailColor, maxLines = 1)
        }
        return
    }
    Column(base.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(range12(item.start, item.end), fontSize = BlockTimeSize, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            InfoIcon(detailColor)
        }
        Text(item.title, fontSize = BlockTitleSize, fontWeight = titleWeight, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, fontSize = BlockDetailSize, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * The clicked block, in the same card style as the rest of Today's side column (SideCard's white
 * bordered box, heading and text sizes, DoneLine-style tick circles) — not the calendar's pop-up.
 */
@Composable
private fun SelectedBlockCard(
    item: DayItem,
    doneIds: Set<Long>,
    completingIds: Set<Long>,
    onTick: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onClose: () -> Unit,
) {
    // Same look as Up next: tinted in the block's colour, name with its time on the right, the task
    // count underneath, then the tasks.
    val ink = MaterialTheme.colorScheme.onSurface
    SideCard("Selected", background = item.color, trailing = {
        Box(Modifier.size(24.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            WizardIconGlyph(WizardGlyph.CLOSE, ink, Modifier.size(11.dp))
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, style = SidePrimary, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(range12(item.start, item.end), style = SideSecondary, color = ink.copy(alpha = 0.8f), maxLines = 1)
            }
            item.tag?.let { Text(it, style = SideSecondary, color = ink.copy(alpha = 0.8f), maxLines = 1) }
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.08f)))
        if (item.subTasks.isEmpty()) {
            Text(
                if (item.kind == TimelineItemKind.CALENDAR_EVENT) "From your calendar." else "A fixed commitment from your Plan settings.",
                style = SidePrimary,
                color = ink.copy(alpha = 0.8f),
            )
        } else {
            BlockTaskList(item, doneIds, completingIds, onTick, onOpenTask, onTint = true)
        }
    }
}

/** A block's tasks with tick circles and a done count — shared by the selected-block card and Up next. */
@Composable
private fun BlockTaskList(item: DayItem, doneIds: Set<Long>, completingIds: Set<Long>, onTick: (Long) -> Unit, onOpenTask: (Long) -> Unit, onTint: Boolean = false, faded: Boolean = false) {
    // On a tinted (category-coloured) card the usual grey is too faint, so everything uses full ink.
    // A finished (grey) block fades its tasks to match — still tickable, just quieter.
    val muted = when {
        faded -> PastText
        onTint -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val nameInk = if (faded) PastText else MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val done = item.subTasks.count { it.taskId in doneIds }
        Text("$done of ${item.subTasks.size} done", style = SideSecondary, color = muted, modifier = Modifier.padding(top = 4.dp))
        item.subTasks.forEach { sub ->
            val isDone = sub.taskId in doneIds
            val completing = sub.taskId in completingIds
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(if (isDone) WebAccent else Color.Transparent)
                        .border(2.dp, if (isDone) WebAccent else muted, CircleShape)
                        .clickable(enabled = !isDone && !completing) { onTick(sub.taskId) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        completing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = WebAccent)
                        isDone -> WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(13.dp))
                    }
                }
                Text(
                    sub.name,
                    style = SidePrimary.copy(textDecoration = if (isDone) TextDecoration.LineThrough else null),
                    color = if (isDone) muted else nameInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { onOpenTask(sub.taskId) },
                )
            }
        }
    }
}

@Composable
private fun SideCard(title: String, background: Color = Color.White, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .border(1.dp, if (background == Color.White) MaterialTheme.colorScheme.outlineVariant else background)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SideHeading, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

private fun longDateLabel(date: kotlinx.datetime.LocalDate): String {
    val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$day ${date.day} $month ${date.year}"
}

@Composable
private fun DoneLine(name: String, note: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(WebAccent), contentAlignment = Alignment.Center) {
            WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(13.dp))
        }
        Text(name, style = SidePrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        note?.let { Text(it, style = SideSecondary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}

/** The right column's three text styles — every card uses only these, so the column reads as one. */
private val SideHeading = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val SidePrimary = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
private val SideSecondary = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)

/** Timeline items for one day from its planned blocks, commitments and calendar events (+ that day's all-day event titles). */
private fun buildDayItems(
    blocks: List<ScheduledBlockResponse>,
    commitments: List<CommitmentBlockResponse>,
    eventItems: List<DisplayItem>,
): Pair<List<DayItem>, List<String>> {
    val timedEvents = eventItems.filter { !it.isAllDay }
    val entries = mergeBlocksIntoEntries(blocks, commitments) + commitmentsToEntries(commitments.excludingFilled(blocks, timedEvents))
    val items = entries.map { e ->
        DayItem(
            title = e.title,
            timeLabel = e.timeLabel,
            tag = e.tag,
            start = e.startMinutes,
            end = e.endMinutes,
            color = if (e.kind == TimelineItemKind.COMMITMENT) WebCommitmentColor else (e.colorHex.toColorOrNull() ?: Color.Gray).webPastel(),
            kind = e.kind,
            subTasks = e.subTasks.filter { it.taskId >= 0 },
        )
    } + timedEvents.map { ev ->
        DayItem(ev.title, ev.timeLabel, ev.subtitle, ev.startMinutes, ev.endMinutes, WebCalendarEventColor, TimelineItemKind.CALENDAR_EVENT, emptyList())
    }
    return items to eventItems.filter { it.isAllDay }.map { it.title }
}

@Composable
private fun DayArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        WizardIconGlyph(WizardGlyph.CHEVRON, if (enabled) MaterialTheme.colorScheme.onSurface else PaleDay, Modifier.size(14.dp).rotate(if (left) 90f else -90f))
    }
}

/** A missed task on a past day: tick circle (ticks it off now), its name, and when it was planned. */
@Composable
private fun MissedLine(item: ReviewItem, done: Boolean, completing: Boolean, onTick: () -> Unit, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (done) WebAccent else Color.Transparent)
                .border(2.dp, if (done) WebAccent else muted, CircleShape)
                .clickable(enabled = !done && !completing && item.pending, onClick = onTick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                completing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = WebAccent)
                done -> WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(13.dp))
            }
        }
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(
                item.taskName,
                style = SidePrimary.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(item.subcategoryName, clock12(item.startTime.toMinutesOfDay())).joinToString(" · "),
                style = SideSecondary,
                color = muted,
                maxLines = 1,
            )
        }
    }
}

/** Phone: progress as one compact line — count, bar, percentage. */
@Composable
private fun PhoneProgressLine(done: Int, total: Int) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (total == 0) "No tasks planned" else "$done of $total done", style = SideSecondary)
        Box(Modifier.weight(1f).height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(WebAccent))
        }
        if (total > 0) Text("${(fraction * 100).toInt()}%", style = SideSecondary.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Phone: where "now" falls between the day's blocks. */
@Composable
private fun PhoneNowLine(nowMinutes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("NOW ${clock12(nowMinutes).replace(" ", "")}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.background(WebAccent).padding(horizontal = 5.dp, vertical = 2.dp))
        Box(Modifier.weight(1f).height(1.5.dp).background(WebAccent))
    }
}

/**
 * Phone: one block of the day — tinted in its category colour (grey once finished), name and time,
 * then its done count. Tapping opens its tasks in place to tick; finished blocks still open, so a
 * task done late can be ticked afterwards. Blocks without tasks (Work, Tea, events) don't open.
 */
@Composable
private fun PhoneDayRow(
    item: DayItem,
    past: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    doneIds: Set<Long>,
    completingIds: Set<Long>,
    onTick: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    val hasTasks = item.subTasks.isNotEmpty()
    val done = item.subTasks.count { it.taskId in doneIds }
    val ink = if (past) PastText else MaterialTheme.colorScheme.onSurface
    val soft = if (past) PastText else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Column(
        Modifier.fillMaxWidth()
            .background(if (past) PastBlock else item.color)
            .then(if (hasTasks) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = SidePrimary.copy(fontWeight = if (item.kind == TimelineItemKind.CALENDAR_EVENT) FontWeight.Normal else FontWeight.Bold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(range12(item.start, item.end), style = SideSecondary, color = soft, maxLines = 1)
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    hasTasks -> if (done == 0) (if (item.subTasks.size == 1) "1 task" else "${item.subTasks.size} tasks") else "$done of ${item.subTasks.size} done"
                    item.kind == TimelineItemKind.CALENDAR_EVENT -> item.tag ?: "Calendar event"
                    else -> "Fixed"
                },
                style = SideSecondary,
                color = soft,
                modifier = Modifier.weight(1f),
            )
            if (hasTasks) WizardIconGlyph(WizardGlyph.CHEVRON, soft, Modifier.size(12.dp).rotate(if (expanded) 0f else -90f))
        }
        if (expanded && hasTasks) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = if (past) 0.05f else 0.08f)))
            BlockTaskList(item, doneIds, completingIds, onTick, onOpenTask, onTint = true, faded = past)
        }
    }
}
