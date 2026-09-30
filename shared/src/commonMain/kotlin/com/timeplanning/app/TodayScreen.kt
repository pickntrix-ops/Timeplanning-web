package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

private val TodayHeaderTop = Color(0xFF3A3358)
private val TodayHeaderBottom = Color(0xFF231C3C)

/**
 * One thing on today's timeline — either a fixed commitment (see SchedulingEngine.PlacedCommitment)
 * or one-or-several contiguous same-category plan blocks merged into a single expandable session
 * (mirrors CalendarScreen's groupingAdjacentPlanBlocks, reimplemented locally rather than shared —
 * this needs each merged task's own id, to complete it individually, which CalendarScreen's shared
 * DisplayItem never needed).
 */
internal data class TodayEntry(
    val title: String,
    val timeLabel: String,
    val tag: String?,
    val startMinutes: Int,
    val endMinutes: Int,
    val colorHex: String,
    val categoryId: Long?,
    val kind: TimelineItemKind,
    val subTasks: List<TodaySubTask>,
)

internal data class TodaySubTask(val taskId: Long, val name: String, val timeLabel: String)

private sealed interface EntryIcon {
    data class Category(val icon: CategoryIcon) : EntryIcon
    data class Wizard(val icon: WizardGlyph) : EntryIcon
}

@Composable
private fun EntryIconGlyph(icon: EntryIcon, tint: Color, modifier: Modifier = Modifier) {
    when (icon) {
        is EntryIcon.Category -> CategoryIconGlyph(icon.icon, tint, modifier)
        is EntryIcon.Wizard -> WizardIconGlyph(icon.icon, tint, modifier)
    }
}

/** A fixed commitment with no linked category (e.g. "Wake up", "Work" straight off the Plan
 * screen) has no CategoryIcon to show — a small keyword guess from its own label at least gets the
 * common ones (waking up, working, meals, winding down) a sensible icon instead of one generic
 * fallback for everything. */
private fun fallbackIconFor(label: String): EntryIcon {
    val lower = label.lowercase()
    return when {
        "wake" in lower -> EntryIcon.Wizard(WizardGlyph.SUN)
        "free" in lower || "sleep" in lower || "bed" in lower || "wind" in lower -> EntryIcon.Wizard(WizardGlyph.MOON)
        "work" in lower -> EntryIcon.Category(CategoryIcon.BRIEFCASE)
        "tea" in lower || "lunch" in lower || "dinner" in lower || "breakfast" in lower || "meal" in lower -> EntryIcon.Category(CategoryIcon.DRINK)
        "person" in lower -> EntryIcon.Wizard(WizardGlyph.PERSON)
        else -> EntryIcon.Wizard(WizardGlyph.CLOCK)
    }
}

private fun iconFor(entry: TodayEntry, categoryIcons: Map<Long, CategoryIcon>): EntryIcon =
    entry.categoryId?.let { categoryIcons[it] }?.let { EntryIcon.Category(it) } ?: fallbackIconFor(entry.title)

private fun timeRangeLabel(startMinutes: Int, endMinutes: Int): String =
    "${(startMinutes / 60).pad2()}:${(startMinutes % 60).pad2()}–${(endMinutes / 60).pad2()}:${(endMinutes % 60).pad2()}"

private data class RawSubTask(val taskId: Long, val name: String, val subcategoryName: String?, val start: Int, val end: Int)

/** Merges contiguous same-category plan blocks into one expandable entry — see TodayEntry's own doc comment for why this doesn't just reuse CalendarScreen's groupingAdjacentPlanBlocks. */
internal fun mergeBlocksIntoEntries(blocks: List<ScheduledBlockResponse>, commitments: List<CommitmentBlockResponse>): List<TodayEntry> {
    val sorted = blocks.sortedBy { it.startTime.toMinutesOfDay() }
    val result = mutableListOf<TodayEntry>()
    var i = 0
    while (i < sorted.size) {
        val first = sorted[i]
        var endMinutes = first.endTime.toMinutesOfDay()
        val startMinutes = first.startTime.toMinutesOfDay()
        val raw = mutableListOf(RawSubTask(first.taskId, first.taskName, first.subcategoryName, startMinutes, endMinutes))
        var j = i + 1
        while (j < sorted.size && sorted[j].categoryName == first.categoryName && sorted[j].startTime.toMinutesOfDay() == endMinutes) {
            val next = sorted[j]
            val nextEnd = next.endTime.toMinutesOfDay()
            raw += RawSubTask(next.taskId, next.taskName, next.subcategoryName, endMinutes, nextEnd)
            endMinutes = nextEnd
            j++
        }
        // A merged block keeps "Category · Subcategory" as its heading when every task in it shares
        // one subcategory (e.g. "Daily · Morning") — same as CalendarScreen's own grouping. When they
        // differ (e.g. Cleaning across several rooms), the heading falls back to the category alone
        // and each sub-task line is prefixed with its own subcategory instead, so that context isn't
        // lost either way.
        val sameSubcategory = raw.map { it.subcategoryName }.distinct().singleOrNull()
        val heading = if (sameSubcategory != null) "${first.categoryName} · $sameSubcategory" else first.categoryName
        val subTasks = raw.map { r ->
            val label = if (sameSubcategory == null && r.subcategoryName != null) "${r.subcategoryName}: ${r.name}" else r.name
            TodaySubTask(r.taskId, label, timeRangeLabel(r.start, r.end))
        }
        // When this cluster is what's actually filling a fixed commitment (the engine packs a
        // commitment's matching tasks back-to-back from its start — see SchedulingEngine's
        // commitment-filling pass), the card should always show the commitment's own reserved
        // window, not just however much of it the tasks happened to use — a "Get ready" commitment
        // set for 08:30-09:00 stays 08:30-09:00 on screen even when only 14 minutes of tasks are
        // currently in it, since the other 16 minutes are still reserved, not free for anything else.
        val matchingCommitment = commitments.firstOrNull { c ->
            c.categoryId == first.categoryId &&
                (c.subcategoryName == null || c.subcategoryName == sameSubcategory) &&
                c.startTime.toMinutesOfDay() <= startMinutes && endMinutes <= c.endTime.toMinutesOfDay()
        }
        val displayStart = matchingCommitment?.startTime?.toMinutesOfDay() ?: startMinutes
        val displayEnd = matchingCommitment?.endTime?.toMinutesOfDay() ?: endMinutes
        result += TodayEntry(
            title = heading,
            timeLabel = timeRangeLabel(displayStart, displayEnd),
            tag = if (subTasks.size > 1) "${subTasks.size} tasks" else null,
            startMinutes = displayStart,
            endMinutes = displayEnd,
            colorHex = first.categoryColor ?: first.categoryName.hashToPaletteColor(),
            categoryId = first.categoryId,
            kind = TimelineItemKind.PLAN_BLOCK,
            subTasks = subTasks,
        )
        i = j
    }
    return result
}

internal fun commitmentsToEntries(commitments: List<CommitmentBlockResponse>): List<TodayEntry> =
    commitments.map { c ->
        val start = c.startTime.toMinutesOfDay()
        val end = c.endTime.toMinutesOfDay()
        TodayEntry(
            title = c.categoryName ?: c.label,
            timeLabel = timeRangeLabel(start, end),
            tag = "Fixed",
            startMinutes = start,
            endMinutes = end,
            colorHex = c.categoryColor ?: (c.categoryName ?: c.label).hashToPaletteColor(),
            categoryId = c.categoryId,
            kind = TimelineItemKind.COMMITMENT,
            // A commitment with a category shows its own label ("Weights") as a single sub-line;
            // one with none (e.g. plain "Work") would just repeat its own title, so it's dropped.
            subTasks = if (c.categoryName != null) listOf(TodaySubTask(-1, c.label, timeRangeLabel(start, end))) else emptyList(),
        )
    }

private fun dateHeaderLabel(date: LocalDate): String {
    val day = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$day, ${date.day} $month ${date.year}"
}

/**
 * The plan/scheduling engine (SchedulingEngine.kt, server-side) generates and persists a week of
 * task placements; this screen shows just today's slice of whatever's currently stored, as a single
 * timeline (fixed commitments and auto-scheduled tasks interleaved by time, contiguous same-category
 * tasks merged into one expandable session — see TodayEntry), plus a manual "refresh" action that
 * re-plans just today (PlanGenerationService.regenerateDay) — which is also how a missed
 * (still-PENDING) task from an earlier day gets swept forward onto today, with priority over fresh
 * backlog (see rolloverCount). Deliberately day-scoped, not a full week regenerate: the rest of the
 * week, wherever it's already settled, is left completely alone by this screen — a full re-plan of
 * the whole week lives on the Calendar screen instead. The week also regenerates automatically once,
 * every Sunday evening (WeeklyAutoRegenJob, server-side) — nothing else happens silently. Only real
 * tasks (never a fixed commitment, which has no underlying Task to mark done) get a completion
 * checkbox.
 */
@Composable
fun TodayScreen(
    apiClient: ApiClient,
    sessionToken: String,
    currentTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (Task, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    // The server defaults a weekStart-less request to the *next* Monday, which skips today entirely
    // whenever today isn't itself a Monday — this screen always needs the week that actually
    // contains today, including when that Monday is in the past (e.g. viewing on a Thursday).
    val thisMonday = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var allTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var categoryColors by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var categoryIcons by remember { mutableStateOf<Map<Long, CategoryIcon>>(emptyMap()) }
    var refreshKey by remember { mutableStateOf(0) }

    var entries by remember { mutableStateOf<List<TodayEntry>>(emptyList()) }
    var refreshingPlan by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var completingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Only today's — the Calendar screen shows the rest of the week's (see ClashCard).
    var clashes by remember { mutableStateOf<List<CalendarClash>>(emptyList()) }
    var resolvingClashKey by remember { mutableStateOf<String?>(null) }
    var allDayPrompts by remember { mutableStateOf<List<AllDayPrompt>>(emptyList()) }

    fun toTodayEntries(blocks: List<ScheduledBlockResponse>, commitments: List<CommitmentBlockResponse>): List<TodayEntry> {
        val todayStr = today.toString()
        val todaysBlocks = blocks.filter { it.date == todayStr }
        val todaysCommitments = commitments.filter { it.date == todayStr }
        // No `events` here — this screen doesn't fetch or show calendar events at all, unlike
        // CalendarScreen, so there's nothing to exclude commitments against on that front.
        return (mergeBlocksIntoEntries(todaysBlocks, todaysCommitments) + commitmentsToEntries(todaysCommitments.excludingFilled(blocks, emptyList())))
            .sortedBy { it.startMinutes }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val fetchedTasks = apiClient.fetchTasks(sessionToken)
            val categories = apiClient.fetchTaskCategories(sessionToken)
            fetchedTasks to categories
        }.onSuccess { (fetchedTasks, categories) ->
            allTasks = fetchedTasks
            categoryColors = categories.associate { it.id to it.displayColor }
            categoryIcons = categories.associate { it.id to it.displayIcon }
        }.onFailure { error = "Couldn't load tasks: ${it.message}" }
        loading = false

        runCatching { apiClient.fetchPlanBlocks(sessionToken, thisMonday.toString()) }
            .onSuccess { response -> entries = toTodayEntries(response.blocks, response.commitments) }
        clashes = runCatching { apiClient.fetchClashes(sessionToken) }.getOrDefault(emptyList()).filter { it.date == today.toString() }
        allDayPrompts = runCatching { apiClient.fetchAllDayPrompts(sessionToken) }.getOrDefault(emptyList())
            .filter { it.firstDate <= today.toString() && today.toString() <= it.lastDate }
    }

    fun resolveAllDay(prompt: AllDayPrompt, choice: AllDayChoice) {
        resolvingClashKey = prompt.seriesKey
        scope.launch {
            runCatching { apiClient.resolveAllDay(sessionToken, prompt, choice) }
                .onSuccess { refreshKey++ }
                .onFailure { planError = "Couldn't save that: ${it.serverMessage()}" }
            resolvingClashKey = null
        }
    }

    fun resolveClash(clash: CalendarClash, action: ClashAction) {
        resolvingClashKey = clash.eventKey
        scope.launch {
            runCatching { apiClient.resolveClash(sessionToken, clash, action) }
                .onSuccess { refreshKey++ }
                .onFailure { planError = "Couldn't sort out the clash with \"${clash.eventTitle}\": ${it.serverMessage()}" }
            resolvingClashKey = null
        }
    }

    // Day-scoped, not a full week regenerate — this screen only ever shows today, so this only ever
    // touches today (see PlanGenerationService.regenerateDay); the rest of the week, wherever it's
    // already settled, is left completely alone. A full-week re-plan lives on the Calendar screen
    // instead, where it actually makes sense to trigger one.
    fun refreshPlan() {
        refreshingPlan = true
        planError = null
        scope.launch {
            runCatching { apiClient.regenerateDay(sessionToken, today.toString()) }
                .onSuccess { response ->
                    entries = toTodayEntries(response.blocks, response.commitments)
                    clashes = emptyList() // re-planning today accounts for everything now in its calendar
                }
                .onFailure { planError = "Couldn't refresh plan: ${it.serverMessage()}" }
            refreshingPlan = false
        }
    }

    // Optimistic-ish: the tapped id shows a spinner immediately, then the full task list is
    // refetched (cheap — it's already a small per-user list) so completedTaskIds and the
    // no-due-date banner both stay correct, including a recurring task's fresh next due date.
    fun completeTask(taskId: Long) {
        completingIds = completingIds + taskId
        scope.launch {
            runCatching { apiClient.completeTask(sessionToken, taskId) }
            runCatching { apiClient.fetchTasks(sessionToken) }.onSuccess { allTasks = it }
            completingIds = completingIds - taskId
        }
    }

    val completedTaskIds = remember(allTasks) { allTasks.filter { it.status == TaskStatus.COMPLETED }.map { it.id }.toSet() }
    val noDateCount = remember(allTasks) { allTasks.count { it.status == TaskStatus.PENDING && it.dueDate == null } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
        bottomBar = { BottomNavBar(current = currentTab, onSelect = onSelectTab) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(TodayHeaderTop, TodayHeaderBottom)), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Today", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Text(dateHeaderLabel(today), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                    }
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
                            .clickable(enabled = !refreshingPlan, onClick = ::refreshPlan),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (refreshingPlan) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            WizardIconGlyph(WizardGlyph.REPEAT, Color.White, Modifier.size(20.dp))
                        }
                    }
                }
            }

            planError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }

            if (noDateCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = { onSelectTab(BottomTab.CATEGORIES) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠️", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$noDateCount task${if (noDateCount == 1) "" else "s"} with no due date — see Tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            if (!loading && error == null && entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Nothing planned for today yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = ::refreshPlan, enabled = !refreshingPlan) { Text("Refresh my plan") }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                allDayPrompts.forEach { prompt ->
                    AllDayPromptCard(prompt, resolvingClashKey == prompt.seriesKey, { resolveAllDay(prompt, it) }, Modifier.padding(bottom = 12.dp))
                }
                clashes.forEach { clash ->
                    ClashCard(
                        clash,
                        resolving = resolvingClashKey == clash.eventKey,
                        onResolve = { resolveClash(clash, it) },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                entries.forEachIndexed { index, entry ->
                    val key = "${entry.title}|${entry.startMinutes}"
                    TodayTimelineRow(
                        entry = entry,
                        icon = iconFor(entry, categoryIcons),
                        isFirst = index == 0,
                        isLast = index == entries.lastIndex,
                        expanded = entry.subTasks.size <= 1 || key in expandedKeys,
                        completedTaskIds = completedTaskIds,
                        completingIds = completingIds,
                        onToggleExpand = { expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key },
                        onOpenTask = { taskId -> allTasks.find { it.id == taskId }?.let { onOpenTask(it, categoryColors[it.taskCategoryId]) } },
                        onComplete = ::completeTask,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayTimelineRow(
    entry: TodayEntry,
    icon: EntryIcon,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    completedTaskIds: Set<Long>,
    completingIds: Set<Long>,
    onToggleExpand: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onComplete: (Long) -> Unit,
) {
    val accent = entry.colorHex.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val singleTaskId = entry.subTasks.singleOrNull()?.takeIf { entry.kind == TimelineItemKind.PLAN_BLOCK }?.taskId
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Time rail: the start time, a dot on a continuous vertical line — the line is skipped
        // above the very first entry and below the very last so it doesn't dangle off-screen.
        Column(modifier = Modifier.width(46.dp).fillMaxHeight(), horizontalAlignment = Alignment.End) {
            Text(
                entry.timeLabel.substringBefore("–"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, end = 8.dp),
            )
        }
        Column(modifier = Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.weight(1f).width(2.dp).background(if (isFirst) Color.Transparent else lineColor))
            Box(modifier = Modifier.padding(vertical = 12.dp).size(11.dp).clip(CircleShape).background(accent))
            Box(modifier = Modifier.weight(1f).width(2.dp).background(if (isLast) Color.Transparent else lineColor))
        }
        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.14f))
                .let {
                    when {
                        entry.subTasks.size > 1 -> it.clickable(onClick = onToggleExpand)
                        singleTaskId != null -> it.clickable { onOpenTask(singleTaskId) }
                        else -> it
                    }
                }
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    EntryIconGlyph(icon, Color.White, Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(
                        entry.tag?.let { "${entry.timeLabel} · $it" } ?: entry.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    entry.subTasks.size > 1 -> {
                        val doneCount = entry.subTasks.count { it.taskId in completedTaskIds }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$doneCount/${entry.subTasks.size}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accent)
                            WizardIconGlyph(
                                WizardGlyph.CHEVRON,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                            )
                        }
                    }
                    singleTaskId != null -> {
                        CompletionCheckbox(
                            checked = singleTaskId in completedTaskIds,
                            loading = singleTaskId in completingIds,
                            accent = accent,
                            onClick = { onComplete(singleTaskId) },
                        )
                    }
                }
            }

            // A single real task's own checkbox already lives in the header row above — showing it
            // again here as a one-line "sub-list" would just be a redundant second checkbox for the
            // exact same task. Only render this list when there's genuinely more than one line to
            // show, or (a commitment's own single context line, e.g. "Weights" under "Exercise") when
            // there's nothing checkable in the header to begin with.
            val showSubList = entry.subTasks.size > 1 || entry.kind != TimelineItemKind.PLAN_BLOCK
            if (expanded && showSubList) {
                Column(modifier = Modifier.padding(top = 10.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.subTasks.forEach { sub ->
                        // A commitment's own single sub-line (its label, e.g. "Weights" under a
                        // "Exercise" commitment) has no real task behind it (id -1 — see
                        // commitmentsToEntries) — shown for context only, never a checkbox.
                        val checkable = entry.kind == TimelineItemKind.PLAN_BLOCK && sub.taskId >= 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (checkable) it.clickable { onOpenTask(sub.taskId) } else it }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (checkable) {
                                CompletionCheckbox(
                                    checked = sub.taskId in completedTaskIds,
                                    loading = sub.taskId in completingIds,
                                    accent = accent,
                                    onClick = { onComplete(sub.taskId) },
                                )
                            } else {
                                Box(modifier = Modifier.size(22.dp))
                            }
                            Text(sub.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(sub.timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Used by CategoryTasksScreen/CategoryDetailScreen/WebCategoriesScreen's own plain task lists — this
 * screen's own timeline uses CompletionCheckbox instead, which also shows checked/loading state. */
@Composable
internal fun CompleteCheckbox(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    ) {}
}

@Composable
private fun CompletionCheckbox(checked: Boolean, loading: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) accent else Color.Transparent)
            .border(1.5.dp, if (checked) accent else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = !loading && !checked, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = if (checked) Color.White else accent)
            checked -> WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(13.dp))
        }
    }
}
