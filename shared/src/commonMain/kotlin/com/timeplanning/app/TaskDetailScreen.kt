package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch

private fun RecurrenceUnit.pluralLabel(): String = when (this) {
    RecurrenceUnit.D -> "days"
    RecurrenceUnit.W -> "weeks"
    RecurrenceUnit.M -> "months"
    RecurrenceUnit.Y -> "years"
}

private fun completionSummary(task: Task): String = when {
    task.repeatsManually -> "Repeats blank (re-date it yourself when done)"
    task.recurrenceInterval != null && task.recurrenceUnit != null ->
        "Repeats every ${task.recurrenceInterval} ${task.recurrenceUnit.pluralLabel()}"
    else -> "One-off"
}

/**
 * A dedicated read view for a single task — colour-blocked header (using the
 * task's category colour in place of a photo, since tasks here have no
 * image), an overlapping details card, and an Edit action. Reached by
 * tapping a task row on TodayScreen.
 */
@Composable
fun TaskDetailScreen(
    apiClient: ApiClient,
    sessionToken: String,
    task: Task,
    categoryColor: String?,
    onBack: () -> Unit,
    onEdit: (Task) -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = categoryColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary

    fun delete() {
        busy = true
        error = null
        scope.launch {
            runCatching { apiClient.deleteTask(sessionToken, task.id) }
                .onSuccess { onDeleted() }
                .onFailure { error = "Couldn't delete task: ${it.message}" }
            busy = false
        }
    }

    Column(modifier = Modifier.safeContentPadding().fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(accent)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton("←", onClick = onBack)
                Box {
                    RoundIconButton("⋯", onClick = { menuExpanded = true })
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Delete task") }, onClick = { menuExpanded = false; delete() })
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = (-20).dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
                Text(task.taskCategoryName, style = MaterialTheme.typography.labelMedium, color = accent)
            }

            Text(task.name, style = MaterialTheme.typography.headlineSmall)

            val subtitleParts = listOfNotNull(task.subcategoryName, task.personName, task.linkedEvent)
            if (subtitleParts.isNotEmpty()) {
                Text(
                    subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoField("Due date", task.dueDate ?: "No date", Modifier.weight(1f))
                InfoField("Duration", "${task.durationMinutes} min", Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Details", style = MaterialTheme.typography.titleSmall)
                DetailRow("When completed", completionSummary(task), accent)
                if (task.queuePosition != null) DetailRow("Order", task.queuePosition.toString(), accent)
                if (task.rolloverCount > 0) DetailRow("Rolled over", "${task.rolloverCount} time(s)", accent)
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { onEdit(task) },
                enabled = !busy,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Edit task", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RoundIconButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}

@Composable
private fun InfoField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
