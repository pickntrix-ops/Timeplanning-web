package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

private enum class TaskSort(val label: String) {
    DEFAULT("Default"),
    DUE("Due date"),
    NAME("Name"),
}

private data class RecurrenceChip(val label: String, val background: Color, val content: Color)

private fun recurrenceChip(task: Task): RecurrenceChip? {
    val n = task.recurrenceInterval ?: return null
    val unit = task.recurrenceUnit ?: return null
    val label = when (unit) {
        RecurrenceUnit.D -> if (n == 1) "Daily" else "Every $n days"
        RecurrenceUnit.W -> if (n == 1) "Weekly" else "Every $n weeks"
        RecurrenceUnit.M -> if (n == 1) "Monthly" else "Every $n months"
        RecurrenceUnit.Y -> if (n == 1) "Yearly" else "Every $n years"
    }
    val approxDays = n * when (unit) {
        RecurrenceUnit.D -> 1
        RecurrenceUnit.W -> 7
        RecurrenceUnit.M -> 30
        RecurrenceUnit.Y -> 365
    }
    return when {
        approxDays <= 7 -> RecurrenceChip(label, Color(0xFFDDE7FF), Color(0xFF2F4FA8))
        approxDays <= 21 -> RecurrenceChip(label, Color(0xFFD8F0E4), Color(0xFF1F7A4D))
        else -> RecurrenceChip(label, Color(0xFFFBE3D3), Color(0xFF9A4A1F))
    }
}

/**
 * Tasks for a subcategory (or, when no subcategory is given, the whole
 * category): what's planned this week first, then everything, with a sort
 * control. Reached by tapping a subcategory tile or "View all" on the
 * category screen.
 */
@Composable
fun CategoryTasksScreen(
    apiClient: ApiClient,
    sessionToken: String,
    categoryId: Long,
    subcategoryId: Long?,
    onBack: () -> Unit,
    onEdit: (TaskCategory) -> Unit,
    onOpenTask: (Task, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf<TaskCategory?>(null) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TaskSort.DEFAULT) }
    var showAllPlanned by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val cats = apiClient.fetchTaskCategories(sessionToken)
            val allTasks = apiClient.fetchTasks(sessionToken)
            cats to allTasks
        }.onSuccess { (cats, allTasks) ->
            category = cats.find { it.id == categoryId }
            tasks = allTasks.filter {
                it.taskCategoryId == categoryId &&
                    it.status != TaskStatus.ABANDONED &&
                    (subcategoryId == null || it.subcategoryId == subcategoryId)
            }
        }.onFailure { error = "Couldn't load tasks: ${it.message}" }
        loading = false
    }

    val current = category
    val accent = current?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val subcategory = subcategoryId?.let { id -> current?.subcategories?.find { it.id == id } }
    val subName = subcategory?.name

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val weekStart = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val weekEnd = remember(weekStart) { weekStart.plus(6, DateTimeUnit.DAY) }

    val planned = tasks
        .filter { it.status != TaskStatus.COMPLETED && it.dueDate != null && LocalDate.parse(it.dueDate) in weekStart..weekEnd }
        .sortedBy { it.dueDate }
    // Only meaningful for one subcategory's own queue (or a category with no subcategories at
    // all, where every task here shares one flat queue) — a "whole category" view mixing several
    // subcategories' tasks has no single comparable order, so don't attempt to number that one.
    val effectiveOrdered = when {
        subcategory != null -> subcategory.ordered ?: current?.ordered ?: false
        current?.subcategories?.isEmpty() == true -> current.ordered
        else -> false
    }
    val sortedTasks = when (sort) {
        TaskSort.DEFAULT -> if (effectiveOrdered) tasks.sortedBy { it.queuePosition ?: Int.MAX_VALUE } else tasks
        TaskSort.DUE -> tasks.sortedBy { it.dueDate ?: "9999-99-99" }
        TaskSort.NAME -> tasks.sortedBy { it.name.lowercase() }
    }.sortedBy { it.status == TaskStatus.COMPLETED }
    val orderBadges = if (effectiveOrdered && sort == TaskSort.DEFAULT) {
        sortedTasks.filter { it.status != TaskStatus.COMPLETED }.mapIndexed { i, t -> t.id to (i + 1) }.toMap()
    } else emptyMap()
    val visiblePlanned = if (showAllPlanned) planned else planned.take(3)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(176.dp).background(accent)) {
            Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                BackChevronButton(onClick = onBack, background = Color.White, modifier = Modifier.align(Alignment.CenterStart))
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        subName ?: current?.name ?: "Tasks",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    if (subName != null) {
                        Text(current?.name ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                    }
                }
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White).clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋯", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E2E38)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit category") },
                            onClick = {
                                menuExpanded = false
                                current?.let(onEdit)
                            },
                        )
                    }
                }
            }
        }

        if (loading && current == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = -DomeArchDepth)
                .verticalScroll(rememberScrollState())
                .clip(rememberDomeShape(DomeArchDepth))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = DomeArchDepth + 8.dp)) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DomeArchDepth + 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Planned this week · ${planned.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (planned.size > 3) {
                    Text(
                        if (showAllPlanned) "Show less ›" else "View all ›",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { showAllPlanned = !showAllPlanned }.padding(vertical = 4.dp),
                    )
                }
            }

            if (planned.isEmpty()) {
                Text("Nothing planned this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TaskCard {
                    visiblePlanned.forEachIndexed { index, task ->
                        if (index > 0) TaskDivider()
                        TaskRow(
                            task = task,
                            accent = accent,
                            onOpen = { onOpenTask(task, current?.displayColor) },
                            onComplete = { scope.launch { runCatching { apiClient.completeTask(sessionToken, task.id) }; refreshKey++ } },
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                WizardIconGlyph(WizardGlyph.CALENDAR, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(15.dp))
                                Text(
                                    dueLabel(task.dueDate, today),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("All tasks · ${tasks.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { sort = TaskSort.entries[(sort.ordinal + 1) % TaskSort.entries.size] }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WizardIconGlyph(WizardGlyph.ORDER, MaterialTheme.colorScheme.onSurface, Modifier.size(16.dp))
                    Text("Sort: ${sort.label}", style = MaterialTheme.typography.bodyMedium)
                    Text("›")
                }
            }

            if (!loading && tasks.isEmpty()) {
                Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TaskCard {
                    sortedTasks.forEachIndexed { index, task ->
                        if (index > 0) TaskDivider()
                        val chip = recurrenceChip(task)
                        TaskRow(
                            task = task,
                            accent = accent,
                            orderBadge = orderBadges[task.id],
                            onOpen = { onOpenTask(task, current?.displayColor) },
                            onComplete = { scope.launch { runCatching { apiClient.completeTask(sessionToken, task.id) }; refreshKey++ } },
                        ) {
                            if (chip != null) {
                                Text(
                                    chip.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = chip.content,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(chip.background).padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TaskCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun TaskDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun TaskRow(task: Task, accent: Color, onOpen: () -> Unit, onComplete: () -> Unit, orderBadge: Int? = null, subtitle: @Composable () -> Unit) {
    val done = task.status == TaskStatus.COMPLETED
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (orderBadge != null) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(orderBadge.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent)
            }
        }
        if (done) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            CompleteCheckbox(onClick = onComplete)
        }
        Column(
            modifier = Modifier.weight(1f).graphicsLayer(alpha = if (done) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(task.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            subtitle()
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}
