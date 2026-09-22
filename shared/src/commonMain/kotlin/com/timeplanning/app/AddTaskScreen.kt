package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch

private enum class CompletionBehavior(val label: String) {
    ONE_OFF("One-off"),
    SCHEDULED("Repeats on a schedule"),
    MANUAL("Repeats blank"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(apiClient: ApiClient, sessionToken: String, editingTask: Task? = null, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var existingTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loadingOptions by remember { mutableStateOf(true) }

    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var selectedSubcategory by remember { mutableStateOf<TaskSubcategory?>(null) }
    var durationMinutes by remember { mutableStateOf("30") }
    var dueDate by remember { mutableStateOf<String?>(null) }
    var queuePosition by remember { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var personMenuExpanded by remember { mutableStateOf(false) }
    var linkedEvent by remember { mutableStateOf("") }

    var completionBehavior by remember { mutableStateOf(CompletionBehavior.ONE_OFF) }
    var recurrenceInterval by remember { mutableStateOf("") }
    var recurrenceUnit by remember { mutableStateOf(RecurrenceUnit.D) }
    var recurrenceUnitMenuExpanded by remember { mutableStateOf(false) }
    var recurrenceBase by remember { mutableStateOf(RecurrenceBase.DUE_DATE) }
    var recurrenceBaseMenuExpanded by remember { mutableStateOf(false) }

    var showFollowUp by remember { mutableStateOf(false) }
    var followUpTask by remember { mutableStateOf<Task?>(null) }
    var followUpMenuExpanded by remember { mutableStateOf(false) }
    var followUpOffsetDays by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val cats = apiClient.fetchTaskCategories(sessionToken)
            val ppl = apiClient.fetchPeople(sessionToken)
            val tasks = apiClient.fetchTasks(sessionToken)
            Triple(cats, ppl, tasks)
        }.onSuccess { (cats, ppl, tasks) ->
            categories = cats
            people = ppl
            existingTasks = tasks
            editingTask?.let { t ->
                name = t.name
                selectedCategory = cats.find { it.id == t.taskCategoryId }
                selectedSubcategory = selectedCategory?.subcategories?.find { it.id == t.subcategoryId }
                durationMinutes = t.durationMinutes.toString()
                dueDate = t.dueDate
                queuePosition = t.queuePosition?.toString() ?: ""
                selectedPerson = ppl.find { it.id == t.personId }
                linkedEvent = t.linkedEvent ?: ""
                completionBehavior = when {
                    t.repeatsManually -> CompletionBehavior.MANUAL
                    t.recurrenceInterval != null -> CompletionBehavior.SCHEDULED
                    else -> CompletionBehavior.ONE_OFF
                }
                recurrenceInterval = t.recurrenceInterval?.toString() ?: ""
                recurrenceUnit = t.recurrenceUnit ?: RecurrenceUnit.D
                recurrenceBase = t.recurrenceBase
                showFollowUp = t.followUpTaskId != null
                followUpTask = tasks.find { it.id == t.followUpTaskId }
                followUpOffsetDays = t.followUpOffsetDays?.toString() ?: ""
            }
        }
        loadingOptions = false
    }

    val effectiveOrdered = selectedSubcategory?.ordered ?: selectedCategory?.ordered ?: false
    val effectiveLinksToPerson = selectedSubcategory?.linksToPerson ?: selectedCategory?.linksToPerson ?: false
    val effectiveLinksToEvent = selectedSubcategory?.linksToEvent ?: selectedCategory?.linksToEvent ?: false

    fun submit() {
        val category = selectedCategory ?: return
        loading = true
        error = null
        scope.launch {
            runCatching {
                if (editingTask != null) {
                    apiClient.updateTask(
                        sessionToken,
                        editingTask.id,
                        UpdateTaskRequest(
                            name = name,
                            taskCategoryId = category.id,
                            subcategoryId = selectedSubcategory?.id,
                            personId = selectedPerson?.id,
                            linkedEvent = linkedEvent.ifBlank { null },
                            dueDate = dueDate,
                            durationMinutes = durationMinutes.toIntOrNull(),
                            recurrenceInterval = if (completionBehavior == CompletionBehavior.SCHEDULED) recurrenceInterval.toIntOrNull() else null,
                            recurrenceUnit = if (completionBehavior == CompletionBehavior.SCHEDULED) recurrenceInterval.toIntOrNull()?.let { recurrenceUnit } else null,
                            recurrenceBase = recurrenceBase,
                            repeatsManually = completionBehavior == CompletionBehavior.MANUAL,
                            followUpTaskId = if (showFollowUp) followUpTask?.id else null,
                            followUpOffsetDays = if (showFollowUp) followUpOffsetDays.toIntOrNull() else null,
                            queuePosition = queuePosition.toIntOrNull(),
                        ),
                    )
                } else {
                    apiClient.createTask(
                        sessionToken,
                        CreateTaskRequest(
                            name = name,
                            taskCategoryId = category.id,
                            subcategoryId = selectedSubcategory?.id,
                            personId = selectedPerson?.id,
                            linkedEvent = linkedEvent.ifBlank { null },
                            dueDate = dueDate,
                            durationMinutes = durationMinutes.toInt(),
                            recurrenceInterval = if (completionBehavior == CompletionBehavior.SCHEDULED) recurrenceInterval.toIntOrNull() else null,
                            recurrenceUnit = if (completionBehavior == CompletionBehavior.SCHEDULED) recurrenceInterval.toIntOrNull()?.let { recurrenceUnit } else null,
                            recurrenceBase = recurrenceBase,
                            repeatsManually = completionBehavior == CompletionBehavior.MANUAL,
                            followUpTaskId = if (showFollowUp) followUpTask?.id else null,
                            followUpOffsetDays = if (showFollowUp) followUpOffsetDays.toIntOrNull() else null,
                            queuePosition = queuePosition.toIntOrNull(),
                        ),
                    )
                }
            }.onSuccess { onDone() }
                .onFailure { error = "Couldn't ${if (editingTask != null) "save" else "create"} task: ${it.message}" }
            loading = false
        }
    }

    Column(modifier = Modifier.safeContentPadding().fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (editingTask != null) "Edit task" else "Create task", style = MaterialTheme.typography.headlineSmall)
            CloseButton(onClick = onCancel)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (loadingOptions) CircularProgressIndicator()

            FormSection("Task name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Hoover the bedroom") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection("Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        CategoryChip(category, selected = selectedCategory?.id == category.id) {
                            selectedCategory = category
                            selectedSubcategory = null
                            recurrenceBase = category.defaultRecurrenceBase
                        }
                    }
                }
            }

            val subcategories = selectedCategory?.subcategories.orEmpty()
            if (subcategories.isNotEmpty()) {
                FormSection("Subcategory") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableChip("(none)", selectedSubcategory == null) { selectedSubcategory = null }
                        subcategories.forEach { sub ->
                            SelectableChip(sub.name, selectedSubcategory?.id == sub.id) { selectedSubcategory = sub }
                        }
                    }
                }
            }

            FormSection("Schedule") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (min)") },
                        modifier = Modifier.weight(1f),
                    )
                    DueDateField(value = dueDate, onValueChange = { dueDate = it }, label = "Due date", modifier = Modifier.weight(1f))
                }

                if (effectiveOrdered) {
                    OutlinedTextField(
                        value = queuePosition,
                        onValueChange = { queuePosition = it.filter { c -> c.isDigit() } },
                        label = { Text("Order (optional — lower goes first)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (effectiveLinksToPerson || effectiveLinksToEvent) {
                FormSection("Extras") {
                    if (effectiveLinksToPerson) {
                        ExposedDropdownMenuBox(expanded = personMenuExpanded, onExpandedChange = { personMenuExpanded = it }) {
                            OutlinedTextField(
                                value = selectedPerson?.name ?: "(none)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Person") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = personMenuExpanded, onDismissRequest = { personMenuExpanded = false }) {
                                people.forEach { person ->
                                    DropdownMenuItem(text = { Text(person.name) }, onClick = {
                                        selectedPerson = person
                                        personMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }

                    if (effectiveLinksToEvent) {
                        OutlinedTextField(
                            value = linkedEvent,
                            onValueChange = { linkedEvent = it },
                            label = { Text("Linked event (e.g. Christmas, optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            FormSection("When completed") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompletionBehavior.entries.forEach { behavior ->
                        SelectableChip(behavior.label, completionBehavior == behavior) { completionBehavior = behavior }
                    }
                }
                if (completionBehavior == CompletionBehavior.MANUAL) {
                    Text(
                        "Comes back blank when completed, ready to re-date (e.g. Etsy order).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (completionBehavior == CompletionBehavior.SCHEDULED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = recurrenceInterval,
                            onValueChange = { recurrenceInterval = it.filter { c -> c.isDigit() } },
                            label = { Text("Repeats every") },
                            modifier = Modifier.weight(1f),
                        )
                        ExposedDropdownMenuBox(
                            expanded = recurrenceUnitMenuExpanded,
                            onExpandedChange = { recurrenceUnitMenuExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = recurrenceUnit.name,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceUnitMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = recurrenceUnitMenuExpanded, onDismissRequest = { recurrenceUnitMenuExpanded = false }) {
                                RecurrenceUnit.entries.forEach { u ->
                                    DropdownMenuItem(text = { Text(u.name) }, onClick = {
                                        recurrenceUnit = u
                                        recurrenceUnitMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = recurrenceBaseMenuExpanded, onExpandedChange = { recurrenceBaseMenuExpanded = it }) {
                        OutlinedTextField(
                            value = recurrenceBase.label(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("When completed") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceBaseMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = recurrenceBaseMenuExpanded, onDismissRequest = { recurrenceBaseMenuExpanded = false }) {
                            RecurrenceBase.entries.forEach { base ->
                                DropdownMenuItem(text = { Text(base.label()) }, onClick = {
                                    recurrenceBase = base
                                    recurrenceBaseMenuExpanded = false
                                })
                            }
                        }
                    }
                }
            }

            FormSection("Follow-up") {
                TextButton(onClick = { showFollowUp = !showFollowUp }) {
                    Text(if (showFollowUp) "Cancel follow-up link" else "+ Link a follow-up task")
                }
                if (showFollowUp) {
                    ExposedDropdownMenuBox(expanded = followUpMenuExpanded, onExpandedChange = { followUpMenuExpanded = it }) {
                        OutlinedTextField(
                            value = followUpTask?.name ?: "(choose a task)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Follow-up task") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = followUpMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = followUpMenuExpanded, onDismissRequest = { followUpMenuExpanded = false }) {
                            existingTasks.forEach { candidate ->
                                DropdownMenuItem(text = { Text(candidate.name) }, onClick = {
                                    followUpTask = candidate
                                    followUpMenuExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = followUpOffsetDays,
                        onValueChange = { followUpOffsetDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Days after this one's completed") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Bottom padding so the last section isn't flush against the pinned button below.
            Box(modifier = Modifier.height(4.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                enabled = name.isNotBlank() && selectedCategory != null && durationMinutes.toIntOrNull() != null && !loading,
                onClick = ::submit,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(if (editingTask != null) "Save changes" else "Create task", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryChip(category: TaskCategory, selected: Boolean, onClick: () -> Unit) {
    val accent = category.displayColor.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val background = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent
    val border = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
        Text(category.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
