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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

private enum class DueGroup(val heading: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    NO_DATE("No date"),
}

private fun dueGroupFor(task: Task, today: LocalDate): DueGroup {
    val date = task.dueDate?.let { LocalDate.parse(it) } ?: return DueGroup.NO_DATE
    return when {
        date < today -> DueGroup.OVERDUE
        date == today -> DueGroup.TODAY
        else -> DueGroup.UPCOMING
    }
}

private fun dueDateLabel(task: Task, today: LocalDate): String {
    val date = task.dueDate?.let { LocalDate.parse(it) } ?: return "No date"
    val days = today.daysUntil(date)
    return when {
        days < 0 -> "Overdue by ${-days} day${if (days == -1) "" else "s"}"
        days == 0 -> "Due today"
        days == 1 -> "Due tomorrow"
        else -> "Due ${task.dueDate}"
    }
}

/**
 * The weekly scheduler (block generation, "Today's blocks") is disconnected
 * pending a follow-up redesign around user-defined Task Categories — see
 * PROJECT_LOG.md. For now this is just the live task list, grouped by
 * due date so what actually needs attention is visible at a glance.
 */
@Composable
fun TodayScreen(
    apiClient: ApiClient,
    sessionToken: String,
    onAddTask: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTaskCategories: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var categoryColors by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val allTasks = apiClient.fetchTasks(sessionToken)
            val categories = apiClient.fetchTaskCategories(sessionToken)
            allTasks to categories
        }.onSuccess { (allTasks, categories) ->
            tasks = allTasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.SCHEDULED }
            categoryColors = categories.associate { it.id to it.displayColor }
        }.onFailure { error = "Couldn't load tasks: ${it.message}" }
        loading = false
    }

    fun refresh() { refreshKey++ }

    // toSortedMap() is JVM-only (java.util.SortedMap) — sort a List<Pair<...>> instead, same fix as CalendarScreen.kt.
    val grouped = remember(tasks) {
        tasks.groupBy { dueGroupFor(it, today) }
            .toList()
            .sortedBy { (group, _) -> group.ordinal }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.safeContentPadding().padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today — $today", style = MaterialTheme.typography.headlineSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenTaskCategories) { Text("Categories") }
                TextButton(onClick = onOpenCalendar) { Text("Calendar") }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onSignOut) {
                        Text("Sign out", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (group, groupTasks) ->
                    item {
                        Text(
                            group.heading,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(groupTasks) { task ->
                        val colorHex = categoryColors[task.taskCategoryId]
                        TaskRow(task, dueDateLabel(task, today), colorHex) {
                            scope.launch {
                                runCatching { apiClient.completeTask(sessionToken, task.id) }
                                refresh()
                            }
                        }
                    }
                }

                if (!loading && tasks.isEmpty()) {
                    item { Text("Nothing here yet. Add a task to get started.", modifier = Modifier.padding(top = 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, dueLabel: String, categoryColorHex: String?, onComplete: (Task) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompleteCheckbox(onClick = { onComplete(task) })

        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape)
                .background(categoryColorHex?.toColorOrNull() ?: Color.Gray),
        )

        Column(Modifier.weight(1f)) {
            Text(task.name)
            Text(
                "${task.taskCategoryName} · ${task.durationMinutes} min · $dueLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompleteCheckbox(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    ) {}
}
