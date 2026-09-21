package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class CompletionBehavior(val label: String) {
    ONE_OFF("One-off"),
    SCHEDULED("Repeats on a schedule"),
    MANUAL("Repeats blank"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(apiClient: ApiClient, sessionToken: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var existingTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loadingOptions by remember { mutableStateOf(true) }

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
        }
        loadingOptions = false
    }

    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var selectedSubcategory by remember { mutableStateOf<TaskSubcategory?>(null) }
    var subcategoryMenuExpanded by remember { mutableStateOf(false) }
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

    val effectiveOrdered = selectedSubcategory?.ordered ?: selectedCategory?.ordered ?: false
    val effectiveLinksToPerson = selectedSubcategory?.linksToPerson ?: selectedCategory?.linksToPerson ?: false
    val effectiveLinksToEvent = selectedSubcategory?.linksToEvent ?: selectedCategory?.linksToEvent ?: false

    Column(
        modifier = Modifier.safeContentPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add task", style = MaterialTheme.typography.headlineSmall)

        if (loadingOptions) CircularProgressIndicator()

        FormSection("Basics") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "(choose a category)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    leadingIcon = selectedCategory?.let { { CategoryDot(it.displayColor) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            leadingIcon = { CategoryDot(category.displayColor) },
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategory = category
                                selectedSubcategory = null
                                recurrenceBase = category.defaultRecurrenceBase
                                categoryMenuExpanded = false
                            },
                        )
                    }
                }
            }

            val subcategories = selectedCategory?.subcategories.orEmpty()
            if (subcategories.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = subcategoryMenuExpanded, onExpandedChange = { subcategoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSubcategory?.name ?: "(none)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subcategory") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = subcategoryMenuExpanded, onDismissRequest = { subcategoryMenuExpanded = false }) {
                        subcategories.forEach { sub ->
                            DropdownMenuItem(text = { Text(sub.name) }, onClick = {
                                selectedSubcategory = sub
                                subcategoryMenuExpanded = false
                            })
                        }
                    }
                }
            }
        }

        FormSection("Schedule") {
            OutlinedTextField(
                value = durationMinutes,
                onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
            )

            DueDateField(value = dueDate, onValueChange = { dueDate = it }, label = "Due date")

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = name.isNotBlank() && selectedCategory != null && durationMinutes.toIntOrNull() != null && !loading,
                onClick = {
                    val category = selectedCategory ?: return@Button
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
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
                        }.onSuccess { onDone() }
                            .onFailure { error = "Couldn't create task: ${it.message}" }
                        loading = false
                    }
                },
            ) {
                Text("Add")
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun CategoryDot(colorHex: String) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colorHex.toColorOrNull() ?: MaterialTheme.colorScheme.outline))
}
