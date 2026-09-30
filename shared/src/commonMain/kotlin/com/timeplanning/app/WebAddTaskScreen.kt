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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The side panel (see WebTaskPanel in App.kt): a close bar, the task name as the title, labelled
    // property rows, and one full-width primary button pinned to the bottom.
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                WizardIconGlyph(WizardGlyph.CLOSE, MaterialTheme.colorScheme.onSurface, Modifier.size(16.dp))
            }
            Text(if (editingTask != null) "Edit task" else "New task", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // The task's name is the panel's title, typed straight into it.
            Box {
                if (name.isEmpty()) Text("Task name", fontSize = 19.sp, color = PaleDay)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(WebAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (loadingOptions) CircularProgressIndicator(Modifier.size(20.dp), color = WebAccent, strokeWidth = 2.dp)

            PanelSectionHeader("Details")
            PanelRow(WizardGlyph.LIST, "Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        val catColor = category.displayColor.toColorOrNull() ?: Color.Gray
                        SquareChip(category.name, selected = selectedCategory?.id == category.id, color = catColor) {
                            selectedCategory = category
                            selectedSubcategory = null
                            recurrenceBase = category.defaultRecurrenceBase
                        }
                    }
                }
            }

            val subcategories = selectedCategory?.subcategories.orEmpty()
            if (subcategories.isNotEmpty()) {
                PanelRow(WizardGlyph.LINK, "Subcategory") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        subcategories.forEach { sub ->
                            val selected = selectedSubcategory?.id == sub.id
                            SquareChip(sub.name, selected, accent) {
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

            PanelSectionHeader("Schedule")
            PanelRow(WizardGlyph.CALENDAR, "Due date") {
                DueDateField(value = dueDate, onValueChange = { dueDate = it }, label = "Due date", plain = true, modifier = Modifier.width(180.dp))
            }

            PanelRow(WizardGlyph.CLOCK, "Duration") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelField(durationMinutes, { durationMinutes = it.filter { c -> c.isDigit() } }, Modifier.width(80.dp))
                    Text("minutes", fontSize = 14.sp, color = muted)
                }
            }

            PanelRow(WizardGlyph.REPEAT, "Repeats") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Switch(checked = repeatable, onCheckedChange = { repeatable = it; if (it) chooseFrequency(frequency) }, colors = SwitchDefaults.colors(checkedTrackColor = WebAccent))
                        Text(if (repeatable) frequencyLabel else "Happens once", fontSize = 14.sp)
                    }
                    if (repeatable) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WebFrequency.entries.forEach { option -> SquareChip(option.label, frequency == option, accent) { chooseFrequency(option) } }
                        }
                        if (frequency == WebFrequency.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Every", fontSize = 14.sp)
                                PanelField(recurrenceInterval, { recurrenceInterval = it.filter { c -> c.isDigit() } }, Modifier.width(70.dp))
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RecurrenceUnit.entries.forEach { u -> SquareChip(u.pluralWeb().replaceFirstChar { it.uppercase() }, recurrenceUnit == u, accent) { recurrenceUnit = u } }
                            }
                        }
                        if (frequency == WebFrequency.MANUAL) {
                            Text("Comes back blank when completed, ready to re-date (e.g. Etsy order).", fontSize = 12.sp, color = muted)
                        } else {
                            Text("Next date is counted from", fontSize = 12.sp, color = muted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RecurrenceBase.entries.forEach { base -> SquareChip(base.shortLabel(), recurrenceBase == base, accent) { recurrenceBase = base } }
                            }
                            Text(recurrenceBase.description(), fontSize = 12.sp, color = muted)
                        }
                    }
                }
            }

            PanelSectionHeader("More")
            if (effectiveLinksToPerson) {
                PanelRow(WizardGlyph.PERSON, "Assigned to") {
                    Box {
                        Text(
                            selectedPerson?.name ?: "Not assigned",
                            fontSize = 14.sp,
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant).clickable { personMenuExpanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        DropdownMenu(expanded = personMenuExpanded, onDismissRequest = { personMenuExpanded = false }, containerColor = Color.White, shape = RectangleShape) {
                            DropdownMenuItem(text = { Text("Not assigned") }, onClick = { selectedPerson = null; personMenuExpanded = false })
                            people.forEach { person -> DropdownMenuItem(text = { Text(person.name) }, onClick = { selectedPerson = person; personMenuExpanded = false }) }
                        }
                    }
                }
            }

            if (effectiveLinksToEvent) {
                PanelRow(WizardGlyph.CALENDAR, "Linked event") {
                    PanelField(linkedEvent, { linkedEvent = it }, Modifier.fillMaxWidth(), placeholder = "e.g. Christmas")
                }
            }

            if (effectiveOrdered) {
                PanelRow(WizardGlyph.ORDER, "Order") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SquareChip("Top of queue", orderChoice == WebOrderChoice.TOP, accent) { orderChoice = WebOrderChoice.TOP }
                        SquareChip("Bottom of queue", orderChoice == WebOrderChoice.BOTTOM, accent) { orderChoice = WebOrderChoice.BOTTOM }
                    }
                }
            }

            PanelRow(WizardGlyph.CHECK_CIRCLE, "Follow-up") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Switch(checked = showFollowUp, onCheckedChange = { showFollowUp = it }, colors = SwitchDefaults.colors(checkedTrackColor = WebAccent))
                        Text(if (showFollowUp) "Queue another task after this one" else "None", fontSize = 14.sp)
                    }
                    if (showFollowUp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                Text(
                                    followUpTask?.name ?: "Choose a task",
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant).clickable { followUpMenuExpanded = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                                DropdownMenu(expanded = followUpMenuExpanded, onDismissRequest = { followUpMenuExpanded = false }, containerColor = Color.White, shape = RectangleShape) {
                                    // Scoped to this task's own category — see AddTaskScreen's mobile
                                    // equivalent for why (showing every task across every category was unusable).
                                    existingTasks
                                        .filter { it.id != editingTask?.id && it.taskCategoryId == selectedCategory?.id }
                                        .forEach { candidate ->
                                            DropdownMenuItem(text = { Text(candidate.name) }, onClick = { followUpTask = candidate; followUpMenuExpanded = false })
                                        }
                                }
                            }
                            PanelField(followUpOffsetDays, { followUpOffsetDays = it.filter { c -> c.isDigit() } }, Modifier.width(60.dp))
                            Text("days after", fontSize = 14.sp, color = muted)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Box(
            Modifier.padding(20.dp).fillMaxWidth().height(52.dp)
                .background(if (canSubmit) WebAccent else WebAccent.copy(alpha = 0.35f))
                .clickable(enabled = canSubmit, onClick = ::submit),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (editingTask != null) "Save changes" else "Create task", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** A thin line and a small heading that starts a group of rows in the panel. */
@Composable
private fun PanelSectionHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One labelled property of the task: icon + label on the left, the control on the right (the reference's "Priority / Status / Due date" rows). */
@Composable
private fun PanelRow(glyph: WizardGlyph, label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Row(Modifier.width(118.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WizardIconGlyph(glyph, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(18.dp))
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** A plain square single-line input with a 1px border — the panel's text field. */
@Composable
private fun PanelField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    Box(modifier.border(1.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty() && placeholder != null) Text(placeholder, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(WebAccent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A square selectable chip — the web's square style, tinted with its colour when selected. */
@Composable
private fun SquareChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(if (selected) color.webPastel() else Color.White)
            .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
