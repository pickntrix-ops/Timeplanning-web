package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class WebFrequency(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom"), MANUAL("Manual"),
}

private enum class WebOrderChoice { TOP, BOTTOM }

private fun RecurrenceUnit.pluralWeb(): String = when (this) {
    RecurrenceUnit.D -> "days"
    RecurrenceUnit.W -> "weeks"
    RecurrenceUnit.M -> "months"
    RecurrenceUnit.Y -> "years"
}

/**
 * Web's create/edit task page — same field set and submit logic as the
 * mobile AddTaskScreen, but a wide two-column desktop form inside
 * WebPageShell instead of a phone-shaped scroll.
 */
@Composable
fun WebAddTaskScreen(apiClient: ApiClient, sessionToken: String, editingTask: Task? = null, onDone: () -> Unit, onCancel: () -> Unit) {
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
    var orderChoice by remember { mutableStateOf(WebOrderChoice.BOTTOM) }
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var personMenuExpanded by remember { mutableStateOf(false) }
    var linkedEvent by remember { mutableStateOf("") }

    var repeatable by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(WebFrequency.WEEKLY) }
    var recurrenceInterval by remember { mutableStateOf("") }
    var recurrenceUnit by remember { mutableStateOf(RecurrenceUnit.D) }
    var recurrenceBase by remember { mutableStateOf(RecurrenceBase.DUE_DATE) }

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
                orderChoice = if (t.queuePosition != null) {
                    val siblingPositions = tasks.filter { sib ->
                        sib.id != t.id && sib.taskCategoryId == t.taskCategoryId && sib.subcategoryId == t.subcategoryId &&
                            sib.status != TaskStatus.COMPLETED && sib.queuePosition != null
                    }.mapNotNull { it.queuePosition }
                    if (siblingPositions.isNotEmpty() && t.queuePosition <= siblingPositions.min()) WebOrderChoice.TOP else WebOrderChoice.BOTTOM
                } else WebOrderChoice.BOTTOM
                selectedPerson = ppl.find { it.id == t.personId }
                linkedEvent = t.linkedEvent ?: ""
                repeatable = t.repeatsManually || t.recurrenceInterval != null
                recurrenceInterval = t.recurrenceInterval?.toString() ?: ""
                recurrenceUnit = t.recurrenceUnit ?: RecurrenceUnit.D
                frequency = when {
                    t.repeatsManually -> WebFrequency.MANUAL
                    t.recurrenceInterval == null -> WebFrequency.WEEKLY
                    t.recurrenceInterval != 1 -> WebFrequency.CUSTOM
                    else -> when (recurrenceUnit) {
                        RecurrenceUnit.D -> WebFrequency.DAILY
                        RecurrenceUnit.W -> WebFrequency.WEEKLY
                        RecurrenceUnit.M -> WebFrequency.MONTHLY
                        RecurrenceUnit.Y -> WebFrequency.YEARLY
                    }
                }
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

    val siblingQueuePositions = existingTasks.filter { sib ->
        sib.id != editingTask?.id && sib.taskCategoryId == selectedCategory?.id && sib.subcategoryId == selectedSubcategory?.id &&
            sib.status != TaskStatus.COMPLETED && sib.queuePosition != null
    }.mapNotNull { it.queuePosition }
    val resolvedQueuePosition = when (orderChoice) {
        WebOrderChoice.TOP -> (siblingQueuePositions.minOrNull() ?: 1) - 1
        WebOrderChoice.BOTTOM -> (siblingQueuePositions.maxOrNull() ?: -1) + 1
    }

    val scheduled = repeatable && frequency != WebFrequency.MANUAL
    val manual = repeatable && frequency == WebFrequency.MANUAL

    fun chooseFrequency(choice: WebFrequency) {
        frequency = choice
        when (choice) {
            WebFrequency.DAILY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.D }
            WebFrequency.WEEKLY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.W }
            WebFrequency.MONTHLY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.M }
            WebFrequency.YEARLY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.Y }
            WebFrequency.CUSTOM -> if ((recurrenceInterval.toIntOrNull() ?: 0) < 1) recurrenceInterval = "2"
            WebFrequency.MANUAL -> {}
        }
    }

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
                            recurrenceInterval = if (scheduled) recurrenceInterval.toIntOrNull() else null,
                            recurrenceUnit = if (scheduled) recurrenceInterval.toIntOrNull()?.let { recurrenceUnit } else null,
                            recurrenceBase = recurrenceBase,
                            repeatsManually = manual,
                            followUpTaskId = if (showFollowUp) followUpTask?.id else null,
                            followUpOffsetDays = if (showFollowUp) followUpOffsetDays.toIntOrNull() else null,
                            queuePosition = if (effectiveOrdered) resolvedQueuePosition else null,
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
                            recurrenceInterval = if (scheduled) recurrenceInterval.toIntOrNull() else null,
                            recurrenceUnit = if (scheduled) recurrenceInterval.toIntOrNull()?.let { recurrenceUnit } else null,
                            recurrenceBase = recurrenceBase,
                            repeatsManually = manual,
                            followUpTaskId = if (showFollowUp) followUpTask?.id else null,
                            followUpOffsetDays = if (showFollowUp) followUpOffsetDays.toIntOrNull() else null,
                            queuePosition = if (effectiveOrdered) resolvedQueuePosition else null,
                        ),
                    )
                }
            }.onSuccess { onDone() }
                .onFailure { error = "Couldn't ${if (editingTask != null) "save" else "create"} task: ${it.serverMessage()}" }
            loading = false
        }
    }

    val accent = selectedCategory?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val fieldColors = webFieldColors(accent)
    val frequencyLabel = when {
        frequency == WebFrequency.CUSTOM && recurrenceInterval.toIntOrNull() != null -> "Every ${recurrenceInterval.toInt()} ${recurrenceUnit.pluralWeb()}"
        else -> frequency.label
    }
    val canSubmit = name.isNotBlank() && selectedCategory != null && durationMinutes.toIntOrNull() != null &&
        (!scheduled || (recurrenceInterval.toIntOrNull() ?: 0) >= 1) && !loading

    WebPageShell(
        title = if (editingTask != null) "Edit task" else "New task",
        onClose = onCancel,
        accent = accent,
        maxWidth = 820.dp,
        actions = {
            AccentButton(
                text = if (editingTask != null) "Save changes" else "Create task",
                onClick = ::submit,
                accent = accent,
                enabled = canSubmit,
                modifier = Modifier.height(44.dp),
                content = if (loading) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
                } else null,
            )
        },
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (loadingOptions) CircularProgressIndicator()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebSectionLabel("Task")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Hoover the bedroom") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebSectionLabel("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            val catAccent = category.displayColor.toColorOrNull() ?: Color.Gray
                            val selected = selectedCategory?.id == category.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) catAccent.copy(alpha = 0.14f) else Color.White)
                                    .border(1.dp, if (selected) catAccent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                                    .clickable {
                                        selectedCategory = category
                                        selectedSubcategory = null
                                        recurrenceBase = category.defaultRecurrenceBase
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryIconGlyph(category.displayIcon, catAccent, Modifier.size(16.dp))
                                Text(category.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                val subcategories = selectedCategory?.subcategories.orEmpty()
                if (subcategories.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WebSectionLabel("Subcategory")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            subcategories.forEach { sub ->
                                val selected = selectedSubcategory?.id == sub.id
                                WebChip(sub.name, selected, accent) {
                                    if (selected) {
                                        selectedSubcategory = null
                                        selectedCategory?.let { recurrenceBase = it.defaultRecurrenceBase }
                                    } else {
                                        selectedSubcategory = sub
                                        selectedCategory?.let { recurrenceBase = sub.effectiveRecurrenceBase(it) }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebSectionLabel("When")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DueDateField(value = dueDate, onValueChange = { dueDate = it }, label = "Due date", shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = durationMinutes,
                            onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                            label = { Text("Duration (min)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WebSectionLabel("Task settings")

                WebSettingsCard {
                    WebSettingRow(WizardGlyph.REPEAT, "Repeatable", if (repeatable) "This task will repeat" else "This task happens once") {
                        Switch(checked = repeatable, onCheckedChange = { repeatable = it; if (it) chooseFrequency(frequency) }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                    }
                    if (repeatable) {
                        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.06f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WebFrequency.entries.forEach { option -> WebChip(option.label, frequency == option, accent) { chooseFrequency(option) } }
                            }
                            if (frequency == WebFrequency.CUSTOM) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Every", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    OutlinedTextField(
                                        value = recurrenceInterval,
                                        onValueChange = { recurrenceInterval = it.filter { c -> c.isDigit() } },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.width(84.dp),
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RecurrenceUnit.entries.forEach { u -> WebChip(u.pluralWeb().replaceFirstChar { it.uppercase() }, recurrenceUnit == u, accent) { recurrenceUnit = u } }
                                }
                            }
                            if (frequency == WebFrequency.MANUAL) {
                                Text(
                                    "Comes back blank when completed, ready to re-date (e.g. Etsy order).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text("Next date is counted", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RecurrenceBase.entries.forEach { base -> WebChip(base.shortLabel(), recurrenceBase == base, accent) { recurrenceBase = base } }
                                }
                                Text(recurrenceBase.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (effectiveLinksToPerson) {
                    WebSettingsCard {
                        WebSettingRow(WizardGlyph.PERSON, "Assign to", "Link this task to a person") {
                            Box {
                                Text(
                                    selectedPerson?.name ?: "Not assigned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { personMenuExpanded = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                                DropdownMenu(expanded = personMenuExpanded, onDismissRequest = { personMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Not assigned") }, onClick = { selectedPerson = null; personMenuExpanded = false })
                                    people.forEach { person -> DropdownMenuItem(text = { Text(person.name) }, onClick = { selectedPerson = person; personMenuExpanded = false }) }
                                }
                            }
                        }
                    }
                }

                if (effectiveLinksToEvent) {
                    WebSettingsCard {
                        WebSettingRow(WizardGlyph.CALENDAR, "Linked event", "e.g. Christmas (optional)") {
                            OutlinedTextField(value = linkedEvent, onValueChange = { linkedEvent = it }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.width(160.dp))
                        }
                    }
                }

                if (effectiveOrdered) {
                    WebSettingsCard {
                        WebSettingRow(WizardGlyph.ORDER, "Order", "Where in the queue this goes") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WebChip("Top", orderChoice == WebOrderChoice.TOP, accent) { orderChoice = WebOrderChoice.TOP }
                                WebChip("Bottom", orderChoice == WebOrderChoice.BOTTOM, accent) { orderChoice = WebOrderChoice.BOTTOM }
                            }
                        }
                    }
                }

                WebSettingsCard {
                    WebSettingRow(WizardGlyph.CHECK_CIRCLE, "Follow-up task", "Queue another task after this one") {
                        Switch(checked = showFollowUp, onCheckedChange = { showFollowUp = it }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                    }
                    if (showFollowUp) {
                        Row(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                Text(
                                    followUpTask?.name ?: "Choose a task",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { followUpMenuExpanded = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                                DropdownMenu(expanded = followUpMenuExpanded, onDismissRequest = { followUpMenuExpanded = false }) {
                                    existingTasks.filter { it.id != editingTask?.id }.forEach { candidate ->
                                        DropdownMenuItem(text = { Text(candidate.name) }, onClick = { followUpTask = candidate; followUpMenuExpanded = false })
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = followUpOffsetDays,
                                onValueChange = { followUpOffsetDays = it.filter { c -> c.isDigit() } },
                                label = { Text("Days after") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSettingsCard(content: @Composable () -> Unit) {
    WebCard(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun WebSettingRow(glyph: WizardGlyph, title: String, description: String, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            WizardIconGlyph(glyph, MaterialTheme.colorScheme.onSurface, Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
