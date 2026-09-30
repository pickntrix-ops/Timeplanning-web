package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private enum class TasksView(val label: String) { WEEK("This week"), CATEGORIES("Categories"), ALL("All tasks"), HISTORY("History") }

private val DoneColor = Color(0xFF3E9B6E)
private val MissedColor = Color(0xFFD0604F)
private val MovedColor = Color(0xFFE0A33A)
private val UnplannedColor = Color(0xFF6B7FD7)

/**
 * The Tasks section's top level, shared by mobile and web: a search bar (find any pending task and
 * tick it off), then This week (everything planned this week, grouped by category), Categories
 * (mobile's category cards, passed in) or — when none are passed, as on web — All tasks (every
 * pending task in plain lists: category, then subcategory, then names), and History (planned vs
 * actually done).
 */
@Composable
internal fun TasksOverview(
    apiClient: ApiClient,
    sessionToken: String,
    allTasks: List<Task>,
    onOpenTask: (Task) -> Unit,
    onTasksChanged: () -> Unit,
    modifier: Modifier = Modifier,
    categoryColors: Map<Long, String> = emptyMap(),
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    categoriesContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var query by remember { mutableStateOf("") }
    var view by remember { mutableStateOf(if (categoriesContent != null) TasksView.CATEGORIES else TasksView.WEEK) }
    var weekTasks by remember { mutableStateOf<List<WeekTask>?>(null) }
    var history by remember { mutableStateOf<HistorySummary?>(null) }
    var historyWeeks by remember { mutableStateOf(4) }
    var completingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var reloadKey by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey, view) {
        if (view == TasksView.WEEK) runCatching { apiClient.fetchWeekTasks(sessionToken) }.onSuccess { weekTasks = it }.onFailure { error = "Couldn't load this week: ${it.message}" }
    }
    LaunchedEffect(reloadKey, view, historyWeeks) {
        if (view == TasksView.HISTORY) runCatching { apiClient.fetchHistory(sessionToken, historyWeeks) }.onSuccess { history = it }.onFailure { error = "Couldn't load history: ${it.message}" }
    }

    fun complete(taskId: Long) {
        completingIds = completingIds + taskId
        scope.launch {
            runCatching { apiClient.completeTask(sessionToken, taskId) }
                .onSuccess { reloadKey++; onTasksChanged() }
                .onFailure { error = "Couldn't tick that off: ${it.serverMessage()}" }
            completingIds = completingIds - taskId
        }
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search tasks to tick off…") },
            leadingIcon = { WizardIconGlyph(WizardGlyph.LIST, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp)) },
            trailingIcon = if (query.isNotEmpty()) {
                { Box(Modifier.clickable { query = "" }.padding(8.dp)) { WizardIconGlyph(WizardGlyph.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(12.dp)) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = horizontalPadding)) }

        if (query.isNotBlank()) {
            val matches = allTasks
                .filter { it.status == TaskStatus.PENDING && it.name.contains(query.trim(), ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
                .take(50)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = horizontalPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (matches.isEmpty()) {
                    Text("No pending task matches \"${query.trim()}\".", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                matches.forEach { task ->
                    TaskTickRow(
                        name = task.name,
                        detail = listOfNotNull(task.taskCategoryName, task.subcategoryName, task.dueDate?.let { "due ${shortDate(it)}" }).joinToString(" · "),
                        color = (categoryColors[task.taskCategoryId] ?: task.taskCategoryName.hashToPaletteColor()).toColorOrNull(),
                        done = false,
                        completing = task.id in completingIds,
                        onTick = { complete(task.id) },
                        onOpen = { onOpenTask(task) },
                    )
                }
            }
            return@Column
        }

        Row(Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TasksView.entries.filter { if (categoriesContent != null) it != TasksView.ALL else it != TasksView.CATEGORIES }.forEach { v ->
                SelectableChip(v.label, view == v) { view = v }
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth()) {
            when (view) {
                TasksView.CATEGORIES -> categoriesContent?.invoke(this)
                TasksView.ALL -> AllTasksList(allTasks, categoryColors, completingIds, horizontalPadding, onTick = ::complete, onOpen = onOpenTask)
                TasksView.WEEK -> WeekTasksList(weekTasks, today, completingIds, horizontalPadding, onTick = ::complete, onOpen = { id -> allTasks.find { it.id == id }?.let(onOpenTask) })
                TasksView.HISTORY -> HistoryPanel(history, historyWeeks, horizontalPadding) { historyWeeks = it }
            }
        }
    }
}

@Composable
private fun TaskTickRow(name: String, detail: String, color: Color?, done: Boolean, completing: Boolean, onTick: () -> Unit, onOpen: () -> Unit) {
    val accent = color ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) accent else Color.Transparent)
                .border(2.dp, accent, CircleShape)
                .clickable(enabled = !done && !completing, onClick = onTick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                completing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
                done -> WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(13.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeekTasksList(
    weekTasks: List<WeekTask>?,
    today: LocalDate,
    completingIds: Set<Long>,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onTick: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    if (weekTasks == null) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontalPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (weekTasks.isEmpty()) {
            Text("Nothing planned this week yet — re-plan the week from the Calendar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        weekTasks.groupBy { it.categoryName }.forEach { (categoryName, tasks) ->
            val color = (tasks.first().categoryColor ?: categoryName.hashToPaletteColor()).toColorOrNull()
            val doneCount = tasks.count { it.isDoneFor(today) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color ?: Color.Gray))
                    Text(categoryName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("$doneCount/${tasks.size} done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tasks.forEach { task ->
                    TaskTickRow(
                        name = task.taskName,
                        detail = listOfNotNull(task.subcategoryName, task.daysLabel()).joinToString(" · "),
                        color = color,
                        done = task.isDoneFor(today),
                        completing = task.taskId in completingIds,
                        onTick = { onTick(task.taskId) },
                        onOpen = { onOpen(task.taskId) },
                    )
                }
            }
        }
    }
}

/** Every pending task as plain lists — category heading, a subheading per subcategory, then just the task names. */
@Composable
private fun AllTasksList(
    allTasks: List<Task>,
    categoryColors: Map<Long, String>,
    completingIds: Set<Long>,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onTick: (Long) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val pending = allTasks.filter { it.status == TaskStatus.PENDING }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontalPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (pending.isEmpty()) Text("No tasks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        pending.groupBy { it.taskCategoryId }.values.sortedBy { it.first().taskCategoryName.lowercase() }.forEach { tasks ->
            val first = tasks.first()
            val color = (categoryColors[first.taskCategoryId] ?: first.taskCategoryName.hashToPaletteColor()).toColorOrNull()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color ?: Color.Gray))
                    Text(first.taskCategoryName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                tasks.groupBy { it.subcategoryName }.entries
                    .sortedWith(compareBy({ it.key == null }, { it.key?.lowercase() }))
                    .forEach { (subcategory, subTasks) ->
                        if (subcategory != null) {
                            Text(subcategory, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                        }
                        subTasks.sortedWith(compareBy({ it.queuePosition ?: Int.MAX_VALUE }, { it.name.lowercase() })).forEach { task ->
                            TaskTickRow(
                                name = task.name,
                                detail = "",
                                color = color,
                                done = false,
                                completing = task.id in completingIds,
                                onTick = { onTick(task.id) },
                                onOpen = { onOpen(task) },
                            )
                        }
                    }
            }
        }
    }
}

/** A daily task counts as done once ticked today; anything else once ticked any day this week (or completed outright). */
private fun WeekTask.isDoneFor(today: LocalDate): Boolean =
    if (isDaily) today.toString() in completedDates else completedDates.isNotEmpty() || status == TaskStatus.COMPLETED

private fun WeekTask.daysLabel(): String =
    if (isDaily) "Every day" else dates.joinToString(", ") { shortDay(it) }

private fun shortDay(iso: String): String = runCatching {
    LocalDate.parse(iso).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
}.getOrDefault(iso)

private fun shortDate(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    "${shortDay(iso)} ${d.day} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
}.getOrDefault(iso)

@Composable
private fun HistoryPanel(summary: HistorySummary?, weeks: Int, horizontalPadding: androidx.compose.ui.unit.Dp, onWeeksChange: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontalPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Last", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(1, 4, 12).forEach { w -> SelectableChip(if (w == 1) "week" else "$w weeks", weeks == w) { onWeeksChange(w) } }
        }
        if (summary == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val rate = if (summary.planned == 0) null else (summary.done * 100f / summary.planned).roundToInt()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Done as planned", rate?.let { "$it%" } ?: "–", "${summary.done} of ${summary.planned}", DoneColor, Modifier.weight(1f))
            StatTile("Missed", summary.missed.toString(), "day passed", MissedColor, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Moved", summary.moved.toString(), "pushed to another day", MovedColor, Modifier.weight(1f))
            StatTile("Done unplanned", summary.unplannedDone.toString(), "not on the plan", UnplannedColor, Modifier.weight(1f))
        }
        if (summary.planned == 0 && summary.days.isEmpty()) {
            Text(
                "History builds up from here — each day's planned tasks are recorded as done, missed or moved once the day is over.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HistorySection("Most productive days") {
            val max = summary.byWeekday.maxOfOrNull { it.done + it.unplannedDone }?.coerceAtLeast(1) ?: 1
            val best = summary.byWeekday.maxByOrNull { it.done + it.unplannedDone }?.takeIf { it.done + it.unplannedDone > 0 }
            best?.let { Text("Most gets done on ${it.day.lowercase().replaceFirstChar { c -> c.uppercase() }}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            summary.byWeekday.forEach { d ->
                val total = d.done + d.unplannedDone
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.day.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                    Row(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (d.done > 0) Box(Modifier.fillMaxHeight().weight(d.done.toFloat()).background(DoneColor))
                        if (d.unplannedDone > 0) Box(Modifier.fillMaxHeight().weight(d.unplannedDone.toFloat()).background(UnplannedColor))
                        if (max - total > 0) Box(Modifier.fillMaxHeight().weight((max - total).toFloat()))
                    }
                    Text(if (d.planned > 0) "${d.done}/${d.planned}" else "$total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                }
            }
            Legend(listOf("Planned & done" to DoneColor, "Done unplanned" to UnplannedColor))
        }

        HistorySection("Time of day things get done") {
            val hours = summary.completionsByHour
            val max = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
            val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
            peak?.let { Text("Busiest hour: ${it.pad2()}:00–${(it + 1).pad2()}:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (6..23).forEach { h ->
                    Box(Modifier.weight(1f).fillMaxHeight(((hours.getOrElse(h) { 0 }).toFloat() / max).coerceAtLeast(0.02f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (h == peak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        if (summary.byCategory.isNotEmpty()) {
            HistorySection("By category") {
                summary.byCategory.forEach { c ->
                    val color = (c.color ?: c.name.hashToPaletteColor()).toColorOrNull() ?: Color.Gray
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        OutcomeCounts(c.done, c.missed, c.moved)
                    }
                }
            }
        }

        if (summary.mostSkipped.isNotEmpty()) {
            HistorySection("Often missed or moved") {
                summary.mostSkipped.forEach { t ->
                    val color = (t.categoryColor ?: t.categoryName.hashToPaletteColor()).toColorOrNull() ?: Color.Gray
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Column(Modifier.weight(1f)) {
                            Text(t.taskName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutcomeCounts(t.done, t.missed, t.moved)
                    }
                }
            }
        }

        if (summary.days.isNotEmpty()) {
            HistorySection("Day by day") {
                summary.days.forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        Text(shortDate(day.date), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        day.items.sortedBy { outcomeOrder(it.outcome) }.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutcomePill(item.outcome)
                                Text(item.taskName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(item.categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun outcomeOrder(outcome: String) = when (outcome) { "DONE" -> 0; "UNPLANNED" -> 1; "MOVED" -> 2; else -> 3 }

@Composable
private fun OutcomePill(outcome: String) {
    val (label, color) = when (outcome) {
        "DONE" -> "Done" to DoneColor
        "MISSED" -> "Missed" to MissedColor
        "MOVED" -> "Moved" to MovedColor
        else -> "Unplanned" to UnplannedColor
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier.width(76.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun OutcomeCounts(done: Int, missed: Int, moved: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("✓ $done", style = MaterialTheme.typography.labelMedium, color = DoneColor)
        Text("✕ $missed", style = MaterialTheme.typography.labelMedium, color = MissedColor)
        Text("↷ $moved", style = MaterialTheme.typography.labelMedium, color = MovedColor)
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = color)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistorySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        content()
    }
}

@Composable
private fun Legend(entries: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
