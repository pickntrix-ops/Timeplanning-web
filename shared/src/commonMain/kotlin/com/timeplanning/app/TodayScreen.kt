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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The weekly scheduler (block generation, "Today's blocks") is disconnected
 * pending a follow-up redesign around user-defined Task Categories — see
 * PROJECT_LOG.md. For now this is just the live task list.
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
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).toString() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching { apiClient.fetchTasks(sessionToken) }
            .onSuccess { allTasks -> tasks = allTasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.SCHEDULED } }
            .onFailure { error = "Couldn't load tasks: ${it.message}" }
        loading = false
    }

    fun refresh() { refreshKey++ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.safeContentPadding().padding(padding).fillMaxSize()) {
            Text(
                "Today — $today",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenTaskCategories) { Text("Categories") }
                TextButton(onClick = onOpenCalendar) { Text("Calendar") }
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

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    TaskRow(task) {
                        scope.launch {
                            runCatching { apiClient.completeTask(sessionToken, task.id) }
                            refresh()
                        }
                    }
                }

                if (!loading && tasks.isEmpty()) {
                    item { Text("Nothing here yet. Add a task to get started.") }
                }
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
            Text("${task.taskCategoryName} · ${task.durationMinutes} min", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onComplete(task) }) { Text("Done") }
    }
}
