package com.timeplanning.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** The scheduling API always takes a Monday — the Monday on/before the given date. */
private fun weekStartFor(date: LocalDate): LocalDate {
    val offset = date.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    return date.minus(DatePeriod(days = offset))
}

@Composable
fun TodayScreen(
    apiClient: ApiClient,
    sessionToken: String,
    onAddTask: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val todayDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val today = remember { todayDate.toString() }
    val weekStart = remember { weekStartFor(todayDate).toString() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var todaysBlocks by remember { mutableStateOf<List<BlockInstance>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val blocks = apiClient.fetchBlockInstances(sessionToken, today, today)
            val allTasks = apiClient.fetchTasks(sessionToken)
            blocks to allTasks
        }.onSuccess { (blocks, allTasks) ->
            todaysBlocks = blocks
            tasks = allTasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.SCHEDULED }
        }.onFailure {
            error = "Couldn't load today: ${it.message}"
        }
        loading = false
    }

    fun refresh() { refreshKey++ }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.safeContentPadding().padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Today — $today", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = {
                    scope.launch {
                        runCatching { apiClient.generateWeek(sessionToken, weekStart) }
                        refresh()
                    }
                },
            ) {
                Text("Generate / refresh this week's schedule")
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (todaysBlocks.isNotEmpty()) {
                    item { Text("Today's blocks", style = MaterialTheme.typography.titleMedium) }
                    items(todaysBlocks) { block ->
                        val blockTasks = tasks.filter { it.blockInstanceId == block.id }
                        BlockCard(block, blockTasks) { task ->
                            scope.launch {
                                runCatching { apiClient.completeTask(sessionToken, task.id) }
                                refresh()
                            }
                        }
                    }
                }

                val unassigned = tasks.filter { it.blockInstanceId == null }
                if (unassigned.isNotEmpty()) {
                    item { Text("Backlog (not yet scheduled)", style = MaterialTheme.typography.titleMedium) }
                    items(unassigned) { task ->
                        TaskRow(task) {
                            scope.launch {
                                runCatching { apiClient.completeTask(sessionToken, task.id) }
                                refresh()
                            }
                        }
                    }
                }

                if (!loading && todaysBlocks.isEmpty() && tasks.isEmpty()) {
                    item { Text("Nothing here yet. Add a task, or generate this week's schedule.") }
                }
            }
        }
    }
}

@Composable
private fun BlockCard(block: BlockInstance, tasks: List<Task>, onComplete: (Task) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${block.taskType.name} — ${block.startTime.take(5)}–${block.endTime.take(5)}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (tasks.isEmpty()) {
                Text("Nothing assigned yet", style = MaterialTheme.typography.bodySmall)
            } else {
                tasks.forEach { TaskRow(it, onComplete) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onComplete: (Task) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(task.name)
            Text("${task.durationMinutes} min", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onComplete(task) }) { Text("Done") }
    }
}
