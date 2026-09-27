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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

private val ProgressGreen = Color(0xFF2E9E6B)
internal val DomeArchDepth = 40.dp

/**
 * A shallow, single-arc dome across the top edge — NOT two RoundedCornerShape
 * corners merging (which, at the radius needed to meet in the middle, always
 * produces a corner-radius-deep semicircle). This is a plain quadratic curve:
 * flush with the flat edge at the far left/right, rising to its peak at centre.
 */
@Composable
internal fun rememberDomeShape(depth: Dp): Shape {
    val density = LocalDensity.current
    return remember(depth, density) {
        GenericShape { size, _ ->
            val h = with(density) { depth.toPx() }
            moveTo(0f, h)
            quadraticTo(size.width / 2f, -h, size.width, h)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

/**
 * The category's own page: a coloured header with a dome, the category's icon
 * and name, a grid of its subcategories (each with this week's progress), and
 * this week's open tasks. Editing lives on its own full screen
 * (CategoryFormScreen), reached from the ⋯ menu.
 */
@Composable
fun CategoryDetailScreen(
    apiClient: ApiClient,
    sessionToken: String,
    categoryId: Long,
    refreshKey: Int,
    onBack: () -> Unit,
    onEdit: (TaskCategory) -> Unit,
    onDeleted: () -> Unit,
    onViewTasks: (Long, Long?) -> Unit,
    onOpenTask: (Task, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf<TaskCategory?>(null) }
    var categoryTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var localRefresh by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, localRefresh) {
        loading = true
        error = null
        runCatching {
            val cats = apiClient.fetchTaskCategories(sessionToken)
            val tasks = apiClient.fetchTasks(sessionToken)
            cats to tasks
        }.onSuccess { (cats, tasks) ->
            category = cats.find { it.id == categoryId }
            categoryTasks = tasks.filter { it.taskCategoryId == categoryId && it.status != TaskStatus.ABANDONED }
        }.onFailure { error = "Couldn't load category: ${it.message}" }
        loading = false
    }

    val current = category
    val accent = current?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val weekStart = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val weekEnd = remember(weekStart) { weekStart.plus(6, DateTimeUnit.DAY) }
    val tasksThisWeek = categoryTasks.filter { it.dueDate != null && LocalDate.parse(it.dueDate) in weekStart..weekEnd }
    val openThisWeek = tasksThisWeek
        .filter { it.status != TaskStatus.COMPLETED }
        .sortedBy { it.dueDate }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(accent)) {
            Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                BackChevronButton(
                    onClick = onBack,
                    background = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
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
                        DropdownMenuItem(
                            text = { Text("Delete category") },
                            onClick = {
                                menuExpanded = false
                                scope.launch {
                                    val result = runCatching { apiClient.deleteTaskCategory(sessionToken, categoryId) }
                                    result.onSuccess { onDeleted() }
                                    result.exceptionOrNull()?.let { error = it.serverMessage() }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (loading && current == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        if (current != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = -DomeArchDepth)
                    .verticalScroll(rememberScrollState())
                    .clip(rememberDomeShape(DomeArchDepth))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DomeArchDepth + 8.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = DomeArchDepth + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(84.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { CategoryIconGlyph(current.displayIcon, accent, Modifier.size(38.dp)) }
                    Text(
                        current.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!current.description.isNullOrBlank()) {
                    Text(
                        current.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Subcategories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("${current.subcategories.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (current.subcategories.isEmpty()) {
                    Text(
                        "No subcategories yet — add some from Edit category.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    current.subcategories.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { sub ->
                                val planned = tasksThisWeek.filter { it.subcategoryId == sub.id }
                                SubcategoryTile(
                                    name = sub.name,
                                    planned = planned.size,
                                    completed = planned.count { it.status == TaskStatus.COMPLETED },
                                    onClick = { onViewTasks(categoryId, sub.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "This week · ${openThisWeek.size} task${if (openThisWeek.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "View all ›",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onViewTasks(categoryId, null) }.padding(vertical = 4.dp),
                    )
                }

                if (openThisWeek.isEmpty()) {
                    Text(
                        "Nothing due this week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    ) {
                        openThisWeek.forEachIndexed { index, task ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            WeekTaskRow(
                                task = task,
                                today = today,
                                onOpen = { onOpenTask(task, current.displayColor) },
                                onComplete = {
                                    scope.launch {
                                        runCatching { apiClient.completeTask(sessionToken, task.id) }
                                        localRefresh++
                                    }
                                },
                            )
                        }
                    }
                }

                Box(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SubcategoryTile(name: String, planned: Int, completed: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val percent = if (planned == 0) 0f else completed.toFloat() / planned
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                Text(
                    if (planned == 0) "No tasks planned" else "$planned planned this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(ProgressGreen),
            )
        }
    }
}

@Composable
private fun WeekTaskRow(task: Task, today: LocalDate, onOpen: () -> Unit, onComplete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompleteCheckbox(onClick = onComplete)
        Column(modifier = Modifier.weight(1f)) {
            Text(task.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            task.subcategoryName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            dueLabel(task.dueDate, today),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

internal fun dueLabel(dueDate: String?, today: LocalDate): String {
    val date = dueDate?.let { LocalDate.parse(it) } ?: return "No date"
    return when (today.daysUntil(date)) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> {
            val day = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$day ${date.day} $month"
        }
    }
}
