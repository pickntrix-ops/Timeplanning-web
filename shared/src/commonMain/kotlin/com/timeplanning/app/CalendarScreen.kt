package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

internal const val DAYS_AHEAD = 13

internal fun Int.pad2() = toString().padStart(2, '0')

/** What kind of thing a DisplayItem represents — drives how TimelineView styles it. */
internal enum class TimelineItemKind { CALENDAR_EVENT, PLAN_BLOCK, COMMITMENT }

/**
 * One thing shown on the timeline — a real Google Calendar event, a
 * generated plan block, or a fixed-commitment occurrence (see
 * PlanGenerationService/SchedulingEngine, server-side), unified into one
 * shape so they render on the same timeline rather than needing three
 * separate rendering paths. Only a calendar event can be all-day. Shared
 * between the mobile and web Calendar screens (see TimelineView's own doc
 * comment for why the rendering logic itself is shared too, unlike most
 * mobile/web screen pairs in this codebase).
 */
internal data class DisplayItem(
    val dateKey: LocalDate,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val isAllDay: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
    val colorHex: String,
    val kind: TimelineItemKind,
    /** The actual task name(s) this block covers — a single-task plan block has one entry; a merged block (see groupingAdjacentPlanBlocks) has several, meant to be listed underneath the category/subcategory heading rather than shown as separate boxes. Empty for calendar events and commitments (their title/subtitle already say everything). */
    val taskNames: List<String> = emptyList(),
    /** The category (and, separately, subcategory) a plan block belongs to — drives merging in
     * groupingAdjacentPlanBlocks. Merging is by categoryName alone, not the full title: three
     * different-room Cleaning tasks (subcategories "Kins", "Johns", ...) packed into one Cleaning
     * commitment are one "Cleaning" block with three tasks listed underneath, not three separate
     * boxes — merging on title would miss this since each task's subcategory differs. Null for
     * calendar events and commitments, which never merge. */
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    /** The task ids behind taskNames, same order — lets the web calendar tick tasks off from a block. */
    val taskIds: List<Long> = emptyList(),
)

/**
 * One DisplayItem per day the event covers. An all-day event's end date is exclusive (Google's
 * convention), so a Sat–Sun event shows on both days; a timed event that runs past midnight is
 * split at each midnight (first day from its start, whole days in between, last day up to its end)
 * — previously both kinds only ever showed on their first day.
 */
internal fun CalendarEventInfo.toDisplayItems(): List<DisplayItem> {
    val color = calendarSummary.hashToPaletteColor()
    if (isAllDay) {
        val first = LocalDate.parse(start)
        val endExclusive = runCatching { LocalDate.parse(end) }.getOrDefault(first.plus(1, DateTimeUnit.DAY))
        return generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it < endExclusive || it == first }
            .map { DisplayItem(it, title, calendarSummary, "All day", true, 0, 0, color, TimelineItemKind.CALENDAR_EVENT) }
            .toList()
    }
    val tz = TimeZone.currentSystemDefault()
    val startLocal = Instant.parse(start).toLocalDateTime(tz)
    val endLocal = Instant.parse(end).toLocalDateTime(tz)
    return generateSequence(startLocal.date) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= endLocal.date }
        .mapNotNull { date ->
            val from = if (date == startLocal.date) startLocal.hour * 60 + startLocal.minute else 0
            val to = if (date == endLocal.date) endLocal.hour * 60 + endLocal.minute else 24 * 60
            if (to <= from) return@mapNotNull null
            val label = "${(from / 60).pad2()}:${(from % 60).pad2()}–${(to / 60 % 24).pad2()}:${(to % 60).pad2()}"
            DisplayItem(date, title, calendarSummary, label, false, from, to, color, TimelineItemKind.CALENDAR_EVENT)
        }
        .toList()
}

/** "HH:mm" or "HH:mm:ss" (the server's own format) -> total minutes since midnight. */
internal fun String.toMinutesOfDay(): Int {
    val parts = split(":")
    return parts[0].toInt() * 60 + parts.getOrElse(1) { "0" }.toInt()
}

/** "Category" alone, or "Category · Subcategory" when there is one — the heading shown for a plan block or commitment, category (and subcategory) first per how the person wants the calendar read: what kind of thing this is, before which specific task it is. */
private fun categoryHeading(categoryName: String?, subcategoryName: String?, fallback: String): String = when {
    categoryName != null && subcategoryName != null -> "$categoryName · $subcategoryName"
    categoryName != null -> categoryName
    else -> fallback
}

internal fun ScheduledBlockResponse.toDisplayItem(): DisplayItem {
    val start = startTime.toMinutesOfDay()
    val end = endTime.toMinutesOfDay()
    val label = "${(start / 60).pad2()}:${(start % 60).pad2()}–${(end / 60).pad2()}:${(end % 60).pad2()}"
    return DisplayItem(
        LocalDate.parse(date), categoryHeading(categoryName, subcategoryName, taskName), taskName, label, false,
        start, end, categoryColor ?: categoryName.hashToPaletteColor(), TimelineItemKind.PLAN_BLOCK, listOf(taskName),
        categoryName, subcategoryName, listOf(taskId),
    )
}

/**
 * Merges consecutive same-category plan blocks (same date, one starting exactly where the previous
 * ends) into a single item spanning their combined time, with every task's name kept in taskNames
 * to list underneath — instead of several tiny boxes for e.g. a commitment packed with a handful of
 * short tasks, which is illegible at timeline scale (the case that prompted this: a "Daily ·
 * Morning" commitment filled with Sun cream, Serums, Hair & face as three separate near-invisible
 * slivers). Merging is by categoryName alone, not the full heading — a "Cleaning" commitment packed
 * with tasks from different rooms/subcategories (subcategory "Kins", "Johns", ...) is still one
 * "Cleaning" box, not three, since merging on the full "Category · Subcategory" title would miss it
 * whenever the subcategory differs per task. When the merged tasks don't all share one subcategory,
 * each line is prefixed with its own subcategory so that context isn't lost, and the block's heading
 * falls back to the category name alone (no single subcategory describes the whole thing anymore).
 * Non-plan-block items (calendar events, commitments) pass through untouched — they're already meant
 * to be their own box.
 */
internal fun List<DisplayItem>.groupingAdjacentPlanBlocks(): List<DisplayItem> {
    val (planBlocks, rest) = partition { it.kind == TimelineItemKind.PLAN_BLOCK }
    val commitmentItems = rest.filter { it.kind == TimelineItemKind.COMMITMENT }
    val eventItems = rest.filter { it.kind == TimelineItemKind.CALENDAR_EVENT }
    val grouped = mutableListOf<DisplayItem>()
    for ((dateKey, dayBlocks) in planBlocks.groupBy { it.dateKey }) {
        val sorted = dayBlocks.sortedBy { it.startMinutes }
        var i = 0
        while (i < sorted.size) {
            var cluster = sorted[i]
            var lines = cluster.taskNames.map { cluster.subcategoryName to it }
            var ids = cluster.taskIds
            var j = i + 1
            while (
                j < sorted.size &&
                sorted[j].categoryName != null &&
                sorted[j].categoryName == cluster.categoryName &&
                sorted[j].startMinutes == cluster.endMinutes
            ) {
                lines = lines + sorted[j].taskNames.map { sorted[j].subcategoryName to it }
                ids = ids + sorted[j].taskIds
                cluster = cluster.copy(endMinutes = sorted[j].endMinutes)
                j++
            }
            val mixedSubcategories = lines.map { it.first }.distinct().size > 1
            val taskNames = if (mixedSubcategories) {
                lines.map { (subcategory, name) -> if (subcategory != null) "$subcategory: $name" else name }
            } else {
                lines.map { it.second }
            }
            val heading = if (mixedSubcategories) (cluster.categoryName ?: cluster.title) else cluster.title
            // When this cluster is what's actually filling a fixed commitment (the engine packs a
            // commitment's matching tasks back-to-back from its own start — see SchedulingEngine's
            // commitment-filling pass), the box should always span the commitment's full reserved
            // window rather than just however much of it the tasks currently use — see the matching
            // fix in TodayScreen's mergeBlocksIntoEntries for the full reasoning.
            val sameSubcategory = if (mixedSubcategories) null else cluster.subcategoryName
            val matchingCommitment = commitmentItems.firstOrNull { c ->
                c.dateKey == dateKey &&
                    c.categoryName == cluster.categoryName &&
                    (c.subcategoryName == null || c.subcategoryName == sameSubcategory) &&
                    c.startMinutes <= cluster.startMinutes && cluster.endMinutes <= c.endMinutes
            }
            val displayStart = matchingCommitment?.startMinutes ?: cluster.startMinutes
            val displayEnd = matchingCommitment?.endMinutes ?: cluster.endMinutes
            val mergedLabel = "${(displayStart / 60).pad2()}:${(displayStart % 60).pad2()}–${(displayEnd / 60).pad2()}:${(displayEnd % 60).pad2()}"
            grouped += cluster.copy(title = heading, taskNames = taskNames, timeLabel = mergedLabel, startMinutes = displayStart, endMinutes = displayEnd, taskIds = ids)
            i = j
        }
    }
    // A commitment already reflected by a (possibly just-widened) plan-block cluster, or by a real
    // calendar event landing on the same time, would just be a redundant duplicate box underneath —
    // same reasoning as the old excludingFilled, just applied after widening now instead of before it,
    // so a commitment used to widen a cluster above doesn't also still show as its own box. A
    // commitment with nothing overlapping it at all still always shows.
    val survivingCommitments = commitmentItems.filter { c ->
        val overlapsBlock = grouped.any { g -> g.dateKey == c.dateKey && g.startMinutes < c.endMinutes && c.startMinutes < g.endMinutes }
        val overlapsEvent = eventItems.any { e -> !e.isAllDay && e.dateKey == c.dateKey && e.startMinutes < c.endMinutes && c.startMinutes < e.endMinutes }
        !overlapsBlock && !overlapsEvent
    }
    return eventItems + survivingCommitments + grouped
}

internal fun CommitmentBlockResponse.toDisplayItem(): DisplayItem {
    val start = startTime.toMinutesOfDay()
    val end = endTime.toMinutesOfDay()
    val timeLabel = "${(start / 60).pad2()}:${(start % 60).pad2()}–${(end / 60).pad2()}:${(end % 60).pad2()}"
    return DisplayItem(
        LocalDate.parse(date), categoryHeading(categoryName, subcategoryName, label), label, timeLabel, false,
        start, end, categoryColor ?: (categoryName ?: label).hashToPaletteColor(), TimelineItemKind.COMMITMENT,
        categoryName = categoryName, subcategoryName = subcategoryName,
    )
}

/**
 * Drops any commitment occurrence that a real plan block, or a real calendar event, already
 * overlaps on the same date — e.g. once a "Weights" commitment is filled with an actual Weights
 * task (see the server's commitment-filling pass), showing the generic commitment box *underneath*
 * that task's own box would just be a redundant, overlapping duplicate of the same real time. Same
 * reasoning for a genuine Google Calendar event landing on the same time as a commitment (the
 * commitment is a routine placeholder, e.g. "Work 09:30-14:00" set up once in Plan settings; a real
 * calendar event there is the truer, specific picture of what's actually happening that day, so the
 * generic commitment box underneath it is just clutter) — see `events`, pre-converted to DisplayItem
 * by the caller so this doesn't need to know about CalendarEventInfo's own (ISO-instant) shape. An
 * all-day event never excludes a commitment, having no specific clock time to overlap. A commitment
 * with nothing overlapping it at all still always shows.
 */
internal fun List<CommitmentBlockResponse>.excludingFilled(blocks: List<ScheduledBlockResponse>, events: List<DisplayItem>): List<CommitmentBlockResponse> =
    filter { commitment ->
        val cDate = LocalDate.parse(commitment.date)
        val cStart = commitment.startTime.toMinutesOfDay()
        val cEnd = commitment.endTime.toMinutesOfDay()
        val overlapsBlock = blocks.any { block ->
            block.date == commitment.date && block.startTime.toMinutesOfDay() < cEnd && cStart < block.endTime.toMinutesOfDay()
        }
        val overlapsEvent = events.any { event ->
            !event.isAllDay && event.dateKey == cDate && event.startMinutes < cEnd && cStart < event.endMinutes
        }
        !overlapsBlock && !overlapsEvent
    }

/**
 * How many minutes tall an item actually renders at — never less than the usual 20-minute
 * legibility floor, and never less than what its own text needs. A grouped block (see
 * groupingAdjacentPlanBlocks) shows a single "N tasks" summary line rather than one line per task
 * (tap it to see the actual list — see TimelineView), so its content is always a title+time row plus
 * one more line — a fixed, small number of lines regardless of how many tasks got packed in, unlike
 * the old one-line-per-task layout where a big cluster of short tasks could inflate the box far past
 * its real duration and make it look like a much longer commitment than it actually was. Shared by
 * assignLanes and TimelineView's own render loop so lane assignment and what's actually drawn always
 * agree — computing this in only one of the two places was the original bug: a grouped block's
 * *rendered* box was taller than lane assignment assumed, so it visually overlapped whatever got
 * placed right after it in the same lane.
 */
internal fun renderedDurationMinutes(item: DisplayItem, hourHeight: Dp): Int {
    val minutesPerLine = 16.dp / hourHeight * 60
    // title+time (one row, not two — see TimelineView) + a second line always — the single task name,
    // the "N tasks" summary, or (for a calendar event/commitment, taskNames empty) the subtitle; every
    // branch of TimelineView's own render `when` draws exactly one more line, so this must match or it
    // clips (that mismatch, not accounted for when the "N tasks" summary line was introduced, was a
    // real regression once already: a single-task block sized for fewer lines than it rendered).
    val contentLines = 2
    val minMinutesForContent = contentLines * minutesPerLine + 12.dp / hourHeight * 60
    return (item.endMinutes - item.startMinutes).coerceAtLeast(20).coerceAtLeast(minMinutesForContent.toInt())
}

/**
 * Greedy interval-graph lane assignment so items that overlap — or, once
 * renderedDurationMinutes's legibility floor is applied, would visually
 * overlap even though their real times don't quite — sit side by side in
 * columns instead of stacking on top of each other unreadably (several short
 * daily-habit plan blocks a few minutes apart was the case that exposed
 * this — before this fix they rendered as an illegible pile of overlapping
 * text). Returns each item's 0-based lane index and the total lane count
 * used across all of them (one global count for the whole set, not per
 * overlapping cluster — simpler, and the wasted width where fewer items
 * overlap is a fair trade).
 */
internal fun assignLanes(items: List<DisplayItem>, hourHeight: Dp): Pair<Map<DisplayItem, Int>, Int> {
    val sorted = items.sortedBy { it.startMinutes }
    val laneEnds = mutableListOf<Int>()
    val laneOf = mutableMapOf<DisplayItem, Int>()
    for (item in sorted) {
        val renderedEnd = item.startMinutes + renderedDurationMinutes(item, hourHeight)
        val laneIndex = laneEnds.indexOfFirst { it <= item.startMinutes }
        if (laneIndex >= 0) {
            laneEnds[laneIndex] = renderedEnd
            laneOf[item] = laneIndex
        } else {
            laneEnds.add(renderedEnd)
            laneOf[item] = laneEnds.size - 1
        }
    }
    return laneOf to laneEnds.size.coerceAtLeast(1)
}

@Composable
fun CalendarScreen(apiClient: ApiClient, sessionToken: String, currentTab: BottomTab, onSelectTab: (BottomTab) -> Unit) {
    val scope = rememberCoroutineScope()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<CalendarEventInfo>>(emptyList()) }
    var planBlocks by remember { mutableStateOf<List<ScheduledBlockResponse>>(emptyList()) }
    var commitments by remember { mutableStateOf<List<CommitmentBlockResponse>>(emptyList()) }
    var calendars by remember { mutableStateOf<List<GoogleCalendarInfo>>(emptyList()) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savingSelection by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var selectedDate by remember { mutableStateOf(today) }
    var generatingNextWeek by remember { mutableStateOf(false) }
    var generatingThisWeek by remember { mutableStateOf(false) }
    // New calendar events landing on the plan — see ClashCard / PlanGenerationService.clashes. Fetched
    // separately from the rest so a clash-check hiccup never blanks the calendar itself.
    var clashes by remember { mutableStateOf<List<CalendarClash>>(emptyList()) }
    var resolvingClashKey by remember { mutableStateOf<String?>(null) }
    var allDayPrompts by remember { mutableStateOf<List<AllDayPrompt>>(emptyList()) }

    // Monday of the current week, and of the week after — between them (this file shows a
    // DAYS_AHEAD=13-day window from today) that covers "the following week" the person asked to
    // see a generated plan for, alongside whatever's left of this week.
    val thisMonday = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val nextMonday = remember(thisMonday) { thisMonday.plus(7, DateTimeUnit.DAY) }

    // A *whole-week* re-plan — every day this week gets recomputed, not just today. TodayScreen's own
    // refresh action deliberately only ever touches today (PlanGenerationService.regenerateDay); this
    // is where the bigger "reshuffle the whole week" action actually belongs, since it's the screen
    // that shows the whole week to begin with. The week also regenerates once automatically, every
    // Sunday evening (WeeklyAutoRegenJob, server-side) — this is for whenever that's not enough.
    fun generateThisWeek() {
        generatingThisWeek = true
        scope.launch {
            runCatching { apiClient.generatePlan(sessionToken, thisMonday.toString()) }
                .onSuccess { response ->
                    planBlocks = planBlocks.filter { it.date >= nextMonday.toString() } + response.blocks
                    commitments = commitments.filter { it.date >= nextMonday.toString() } + response.commitments
                }
                .onFailure { error = "Couldn't re-plan this week: ${it.serverMessage()}" }
            // A fresh plan is built around the calendar as it is now, so its clashes are gone.
            clashes = runCatching { apiClient.fetchClashes(sessionToken) }.getOrDefault(clashes)
            generatingThisWeek = false
        }
    }

    fun generateNextWeek() {
        generatingNextWeek = true
        scope.launch {
            runCatching { apiClient.generatePlan(sessionToken, nextMonday.toString()) }
                .onSuccess { response ->
                    planBlocks = planBlocks.filter { it.date < nextMonday.toString() } + response.blocks
                    commitments = commitments.filter { it.date < nextMonday.toString() } + response.commitments
                }
                .onFailure { error = "Couldn't generate next week's plan: ${it.serverMessage()}" }
            // A fresh plan is built around the calendar as it is now, so its clashes are gone.
            clashes = runCatching { apiClient.fetchClashes(sessionToken) }.getOrDefault(clashes)
            generatingNextWeek = false
        }
    }


    fun resolveAllDay(prompt: AllDayPrompt, choice: AllDayChoice) {
        resolvingClashKey = prompt.seriesKey
        scope.launch {
            runCatching { apiClient.resolveAllDay(sessionToken, prompt, choice) }
                .onSuccess { refreshKey++ }
                .onFailure { error = "Couldn't save that: ${it.serverMessage()}" }
            resolvingClashKey = null
        }
    }

    fun resolveClash(clash: CalendarClash, action: ClashAction) {
        resolvingClashKey = clash.eventKey
        scope.launch {
            runCatching { apiClient.resolveClash(sessionToken, clash, action) }
                .onSuccess { refreshKey++ }
                .onFailure { error = "Couldn't sort out the clash with \"${clash.eventTitle}\": ${it.serverMessage()}" }
            resolvingClashKey = null
        }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val end = today.plus(DAYS_AHEAD, DateTimeUnit.DAY)
            val fetchedEvents = apiClient.fetchCalendarEvents(sessionToken, today.toString(), end.toString())
            val fetchedCalendars = apiClient.fetchCalendars(sessionToken)
            // DAYS_AHEAD spans into next week, so both weeks' stored blocks/commitments are needed to fill the timeline.
            val thisWeek = runCatching { apiClient.fetchPlanBlocks(sessionToken, thisMonday.toString()) }.getOrNull()
            val nextWeek = runCatching { apiClient.fetchPlanBlocks(sessionToken, nextMonday.toString()) }.getOrNull()
            val fetchedBlocks = thisWeek?.blocks.orEmpty() + nextWeek?.blocks.orEmpty()
            val fetchedCommitments = thisWeek?.commitments.orEmpty() + nextWeek?.commitments.orEmpty()
            Triple(fetchedEvents, fetchedCalendars, fetchedBlocks to fetchedCommitments)
        }.onSuccess { (fetchedEvents, fetchedCalendars, blocksAndCommitments) ->
            events = fetchedEvents
            calendars = fetchedCalendars
            planBlocks = blocksAndCommitments.first
            commitments = blocksAndCommitments.second
            pendingSelection = fetchedCalendars.filter { it.selected }.map { it.id }.toSet()
        }.onFailure {
            error = "Couldn't load calendar: ${it.message}"
        }
        clashes = runCatching { apiClient.fetchClashes(sessionToken) }.getOrDefault(emptyList())
        allDayPrompts = runCatching { apiClient.fetchAllDayPrompts(sessionToken) }.getOrDefault(emptyList())
        loading = false
    }

    val displayItems = remember(events, planBlocks, commitments) {
        val eventItems = events.flatMap { it.toDisplayItems() }
        // The full (unfiltered) commitment list goes in here, not excludingFilled's result — grouping
        // needs to see a commitment that's already partly filled by real blocks too, so it can widen
        // that cluster to the commitment's own window (see groupingAdjacentPlanBlocks) before deciding
        // which commitments are now redundant to show as their own separate box.
        (eventItems + planBlocks.map { it.toDisplayItem() } + commitments.map { it.toDisplayItem() })
            .groupingAdjacentPlanBlocks()
    }
    val dateRange = remember(today) { (0..DAYS_AHEAD).map { today.plus(it, DateTimeUnit.DAY) } }
    val dayItems = remember(displayItems, selectedDate) { displayItems.filter { it.dateKey == selectedDate } }
    val allDayEvents = remember(dayItems) { dayItems.filter { it.isAllDay } }
    val timedItems = remember(dayItems) { dayItems.filter { !it.isAllDay }.sortedBy { it.startMinutes } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BottomNavBar(current = currentTab, onSelect = onSelectTab) },
    ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Calendar", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = ::generateThisWeek, enabled = !generatingThisWeek) {
                    if (generatingThisWeek) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Re-plan this week")
                    }
                }
                TextButton(onClick = ::generateNextWeek, enabled = !generatingNextWeek) {
                    if (generatingNextWeek) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Plan next week")
                    }
                }
                TextButton(onClick = { showCalendarPicker = !showCalendarPicker }) {
                    Text(if (showCalendarPicker) "Hide calendars" else "Calendars")
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        if (clashes.isNotEmpty() || allDayPrompts.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allDayPrompts.forEach { prompt ->
                    AllDayPromptCard(prompt, resolvingClashKey == prompt.seriesKey, { resolveAllDay(prompt, it) })
                }
                clashes.forEach { clash ->
                    ClashCard(clash, resolving = resolvingClashKey == clash.eventKey, onResolve = { resolveClash(clash, it) })
                }
            }
        }

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
            !loading && dayItems.isEmpty() -> {
                Text(
                    "Nothing on your calendars for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            timedItems.isNotEmpty() -> {
                TimelineView(items = timedItems, modifier = Modifier.weight(1f).fillMaxWidth())
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
private fun AllDayEventCard(display: DisplayItem) {
    val accent = display.colorHex.toColorOrNull() ?: MaterialTheme.colorScheme.primary
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
        Text(display.title, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Positions each timed item (a real Google Calendar event, or a generated
 * plan block — see DisplayItem) as a coloured block on an hour-ruled
 * timeline, offset/sized proportionally to its start time and duration, so
 * the day's shape is visible at a glance. A plan block is coloured by its
 * task's category rather than hashed from a calendar name, so the two kinds
 * stay visually distinct even sitting side by side. Overlapping (or
 * near-enough, once the 20-min legibility floor applies — see assignLanes)
 * items are placed in side-by-side lanes rather than stacked directly on top
 * of each other, which is illegible. Shared by mobile and web (unlike most
 * screen pairs in this codebase) since this is pure, non-trivial layout
 * logic, same reasoning as PlanScreen's EnergyRangeSlider/DayPreviewTimeline.
 */
@Composable
internal fun TimelineView(items: List<DisplayItem>, modifier: Modifier = Modifier, hourHeight: Dp = 64.dp, hourLabelWidth: Dp = 52.dp) {
    // A grouped block (see groupingAdjacentPlanBlocks) shows a compact "N tasks" summary rather than
    // the full list inline (see the loop below) — tapping it opens the actual list here instead, so
    // the block's on-screen size reflects its real duration rather than ballooning with every task
    // packed into it (a "few short tasks" block used to render as tall as its whole task list needed,
    // which read as a much bigger commitment than it actually was).
    var expandedItem by remember { mutableStateOf<DisplayItem?>(null) }
    val startHour = ((items.minOfOrNull { it.startMinutes } ?: 8 * 60) / 60).coerceAtMost(8)
    // Bounded by whichever is later: an item's real end time, or how far down it actually renders
    // (a grouped block's list can need more room than its real duration — see renderedDurationMinutes)
    // — using only the real end time here meant the scrollable area could be too short to reach a
    // tall grouped block at all, on top of there being no bottom breathing room (this file's own
    // padding(bottom = ...) below covers that second part).
    val maxRenderedEnd = items.maxOfOrNull { it.startMinutes + renderedDurationMinutes(it, hourHeight) } ?: 0
    val maxRealEnd = items.maxOfOrNull { it.endMinutes } ?: (20 * 60)
    val endHour = ((maxOf(maxRealEnd, maxRenderedEnd) + 59) / 60).coerceAtLeast(20).coerceAtMost(24)
    val (laneOf, laneCount) = remember(items, hourHeight) { assignLanes(items, hourHeight) }

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
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

        BoxWithConstraints(modifier = Modifier.padding(start = hourLabelWidth + 8.dp, end = 8.dp).fillMaxWidth()) {
            val laneGap = 4.dp
            val laneWidth = (maxWidth - laneGap * (laneCount - 1)) / laneCount

            items.forEach { item ->
                val clampedStart = item.startMinutes.coerceAtLeast(startHour * 60)
                // A grouped block (several tasks listed inside, see groupingAdjacentPlanBlocks) needs
                // room for every line, not just the usual 20-minute legibility floor — otherwise a
                // short but busy commitment (several 2-minute habits) renders too short to show its
                // own list. minutesPerLine converts the fixed per-line pixel height into "minutes" at
                // this timeline's current scale, so it stays correct if hourHeight ever changes.
                // Matches assignLanes exactly — see renderedDurationMinutes's own doc comment for why
                // computing this in only one of the two places caused blocks to visually overlap.
                val durationMinutes = renderedDurationMinutes(item, hourHeight)
                val topOffset = hourHeight * ((clampedStart - startHour * 60) / 60f)
                val blockHeight = hourHeight * (durationMinutes / 60f)
                val accent = item.colorHex.toColorOrNull() ?: MaterialTheme.colorScheme.primary
                val label = "${(clampedStart / 60).pad2()}:${(clampedStart % 60).pad2()}"
                val lane = laneOf[item] ?: 0

                Column(
                    modifier = Modifier
                        .offset(x = (laneWidth + laneGap) * lane, y = topOffset)
                        .width(laneWidth)
                        .height(blockHeight)
                        .clip(RoundedCornerShape(10.dp))
                        // A plan block (an actual task the engine placed) gets a lighter fill + border
                        // instead of a solid fill, so it reads as "planned" rather than "confirmed", the
                        // way the fixed-commitment/preview pieces on the Plan screen already distinguish
                        // placeholder-ish content. A commitment renders solid like a real calendar event —
                        // both are "this is genuinely happening", not a suggestion.
                        .let { if (item.kind == TimelineItemKind.PLAN_BLOCK) it.background(accent.copy(alpha = 0.16f)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else it.background(accent.copy(alpha = 0.9f)) }
                        .let { if (item.taskNames.size > 1) it.clickable { expandedItem = item } else it }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    val textColor = if (item.kind == TimelineItemKind.PLAN_BLOCK) accent else Color.White
                    // Title and time share one line rather than each getting their own — saves a
                    // whole line of height on every block (a short block now needs one line less
                    // room, see renderedDurationMinutes), which was reading as blocks overlapping
                    // when really they were just packed tightly with room they didn't need to use.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f), maxLines = 1)
                    }
                    when {
                        // Several tasks packed into one block (see groupingAdjacentPlanBlocks) — a
                        // compact count instead of listing every one inline, so the block's height
                        // reflects its real duration rather than how many tasks happen to be in it (see
                        // renderedDurationMinutes). Tap it to see the actual list.
                        item.taskNames.size > 1 -> Text(
                            "${item.taskNames.size} tasks — tap to view",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                        item.taskNames.isNotEmpty() -> Text(
                            "• ${item.taskNames.first()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                        else -> Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
            }
        }
    }

    expandedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { expandedItem = null },
            confirmButton = { TextButton(onClick = { expandedItem = null }) { Text("Close") } },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.timeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.taskNames.forEach { taskName -> Text("• $taskName", style = MaterialTheme.typography.bodyMedium) }
                }
            },
        )
    }
}
