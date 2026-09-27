package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private fun RecurrenceUnit.pluralDetail(): String = when (this) {
    RecurrenceUnit.D -> "days"
    RecurrenceUnit.W -> "weeks"
    RecurrenceUnit.M -> "months"
    RecurrenceUnit.Y -> "years"
}

private fun webCompletionSummary(task: Task): String = when {
    task.repeatsManually -> "Repeats blank (re-date it yourself when done)"
    task.recurrenceInterval != null && task.recurrenceUnit != null -> "Repeats every ${task.recurrenceInterval} ${task.recurrenceUnit.pluralDetail()}"
    else -> "One-off"
}

/** Web's task detail page — a wide WebPageShell card instead of the mobile colour-block header. */
@Composable
fun WebTaskDetailScreen(
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
    val accent = categoryColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary

    fun delete() {
        busy = true
        error = null
        scope.launch {
            runCatching { apiClient.deleteTask(sessionToken, task.id) }
                .onSuccess { onDeleted() }
                .onFailure { error = "Couldn't delete task: ${it.serverMessage()}" }
            busy = false
        }
    }

    WebPageShell(
        title = task.name,
        onClose = onBack,
        accent = accent,
        maxWidth = 640.dp,
        actions = {
            WebChip("Delete", selected = false, accent = MaterialTheme.colorScheme.error, onClick = ::delete)
            AccentButton(text = "Edit task", onClick = { onEdit(task) }, accent = accent, enabled = !busy, modifier = Modifier.height(44.dp))
        },
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Text(task.taskCategoryName, style = MaterialTheme.typography.labelMedium, color = accent)
        }

        val subtitleParts = listOfNotNull(task.subcategoryName, task.personName, task.linkedEvent)
        if (subtitleParts.isNotEmpty()) {
            Text(subtitleParts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WebInfoField("Due date", task.dueDate ?: "No date", Modifier.weight(1f))
            WebInfoField("Duration", "${task.durationMinutes} min", Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WebSectionLabel("Details")
            WebDetailRow("When completed", webCompletionSummary(task), accent)
            if (task.queuePosition != null) WebDetailRow("Order", task.queuePosition.toString(), accent)
            if (task.rolloverCount > 0) WebDetailRow("Rolled over", "${task.rolloverCount} time(s)", accent)
            if (task.followUpTaskName != null) {
                val offset = task.followUpOffsetDays
                WebDetailRow("Then", "${task.followUpTaskName}${if (offset != null) " ($offset day${if (offset == 1) "" else "s"} later)" else ""}", accent)
            }
        }

        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun WebInfoField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WebDetailRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
