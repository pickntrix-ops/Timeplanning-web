package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TaskFormFallbackAccent = Color(0xFF4B3F72)

private enum class Frequency(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom"), MANUAL("Manual"),
}

private enum class OrderChoice { TOP, BOTTOM }

private fun RecurrenceUnit.plural(): String = when (this) {
    RecurrenceUnit.D -> "days"
    RecurrenceUnit.W -> "weeks"
    RecurrenceUnit.M -> "months"
    RecurrenceUnit.Y -> "years"
}

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
    var orderChoice by remember { mutableStateOf(OrderChoice.BOTTOM) }
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var personMenuExpanded by remember { mutableStateOf(false) }
    var linkedEvent by remember { mutableStateOf("") }

    var repeatable by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(Frequency.WEEKLY) }
    var frequencyOpen by remember { mutableStateOf(false) }
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
                    if (siblingPositions.isNotEmpty() && t.queuePosition <= siblingPositions.min()) OrderChoice.TOP else OrderChoice.BOTTOM
                } else OrderChoice.BOTTOM
                selectedPerson = ppl.find { it.id == t.personId }
                linkedEvent = t.linkedEvent ?: ""
                repeatable = t.repeatsManually || t.recurrenceInterval != null
                recurrenceInterval = t.recurrenceInterval?.toString() ?: ""
                recurrenceUnit = t.recurrenceUnit ?: RecurrenceUnit.D
                frequency = when {
                    t.repeatsManually -> Frequency.MANUAL
                    t.recurrenceInterval == null -> Frequency.WEEKLY
                    t.recurrenceInterval != 1 -> Frequency.CUSTOM
                    else -> when (recurrenceUnit) {
                        RecurrenceUnit.D -> Frequency.DAILY
                        RecurrenceUnit.W -> Frequency.WEEKLY
                        RecurrenceUnit.M -> Frequency.MONTHLY
                        RecurrenceUnit.Y -> Frequency.YEARLY
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

    // The other open, ordered tasks this one would be queued alongside — same category+subcategory —
    // used to turn a simple "top or bottom" choice into the actual queuePosition the server wants.
    val siblingQueuePositions = existingTasks.filter { sib ->
        sib.id != editingTask?.id && sib.taskCategoryId == selectedCategory?.id && sib.subcategoryId == selectedSubcategory?.id &&
            sib.status != TaskStatus.COMPLETED && sib.queuePosition != null
    }.mapNotNull { it.queuePosition }
    val resolvedQueuePosition = when (orderChoice) {
        OrderChoice.TOP -> (siblingQueuePositions.minOrNull() ?: 1) - 1
        OrderChoice.BOTTOM -> (siblingQueuePositions.maxOrNull() ?: -1) + 1
    }

    val scheduled = repeatable && frequency != Frequency.MANUAL
    val manual = repeatable && frequency == Frequency.MANUAL

    fun chooseFrequency(choice: Frequency) {
        frequency = choice
        when (choice) {
            Frequency.DAILY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.D }
            Frequency.WEEKLY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.W }
            Frequency.MONTHLY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.M }
            Frequency.YEARLY -> { recurrenceInterval = "1"; recurrenceUnit = RecurrenceUnit.Y }
            Frequency.CUSTOM -> if ((recurrenceInterval.toIntOrNull() ?: 0) < 1) recurrenceInterval = "2"
            Frequency.MANUAL -> {}
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
                .onFailure { error = "Couldn't ${if (editingTask != null) "save" else "create"} task: ${it.message}" }
            loading = false
        }
    }


    val accent = selectedCategory?.displayColor?.toColorOrNull() ?: TaskFormFallbackAccent
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = accent,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        cursorColor = accent,
    )
    val frequencyLabel = when {
        frequency == Frequency.CUSTOM && recurrenceInterval.toIntOrNull() != null ->
            "Every ${recurrenceInterval.toInt()} ${recurrenceUnit.plural()}"
        else -> frequency.label
    }
    val canSubmit = name.isNotBlank() && selectedCategory != null && durationMinutes.toIntOrNull() != null &&
        (!scheduled || (recurrenceInterval.toIntOrNull() ?: 0) >= 1) && !loading
    val domeDepth = 24.dp

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = accent)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = domeDepth + 4.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    BackChevronButton(onClick = onCancel, background = Color.White, modifier = Modifier.align(Alignment.CenterStart))
                    Text(
                        if (editingTask != null) "Edit task" else "New task",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape).background(Color.White).clickable(onClick = onCancel),
                        contentAlignment = Alignment.Center,
                    ) { WizardIconGlyph(WizardGlyph.CLOSE, Color(0xFF2E2E38), Modifier.size(16.dp)) }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = -domeDepth)
                    .clip(rememberDomeShape(domeDepth))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(Modifier.height(domeDepth - 4.dp))
                    if (loadingOptions) CircularProgressIndicator()

                    TaskFormSection("Task") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("e.g. Hoover the bedroom") },
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            trailingIcon = if (name.isNotEmpty()) {
                                {
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFB9B9C2)).clickable { name = "" },
                                        contentAlignment = Alignment.Center,
                                    ) { WizardIconGlyph(WizardGlyph.CLOSE, Color.White, Modifier.size(11.dp)) }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    TaskFormSection("Category") {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            categories.forEach { category ->
                                CategoryTile(category, selected = selectedCategory?.id == category.id) {
                                    selectedCategory = category
                                    selectedSubcategory = null
                                    recurrenceBase = category.defaultRecurrenceBase
                                }
                            }
                        }
                    }

                    val subcategories = selectedCategory?.subcategories.orEmpty()
                    if (subcategories.isNotEmpty()) {
                        TaskFormSection("Subcategory") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                subcategories.forEach { sub ->
                                    val selected = selectedSubcategory?.id == sub.id
                                    TaskChip(sub.name, selected, accent) {
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

                    TaskFormSection("When") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DueDateField(
                                value = dueDate,
                                onValueChange = { dueDate = it },
                                label = "Due date",
                                shape = fieldShape,
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                                label = { Text("Duration (min)") },
                                singleLine = true,
                                shape = fieldShape,
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    TaskFormSection("Task settings") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Repeatable + its frequency panel
                            SettingsCard {
                                SettingRow(
                                    glyph = WizardGlyph.REPEAT,
                                    title = "Repeatable",
                                    description = if (repeatable) "This task will repeat" else "This task happens once",
                                ) {
                                    Switch(
                                        checked = repeatable,
                                        onCheckedChange = {
                                            repeatable = it
                                            if (it) chooseFrequency(frequency)
                                            frequencyOpen = false
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = accent),
                                    )
                                }
                                if (repeatable) {
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(accent.copy(alpha = 0.06f)),
                                    ) {
                                        SettingRow(
                                            glyph = WizardGlyph.CALENDAR,
                                            title = "Frequency",
                                            description = "How often does this task repeat?",
                                            iconBackground = Color.Transparent,
                                            modifier = Modifier.clickable { frequencyOpen = !frequencyOpen },
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    frequencyLabel,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = accent,
                                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 8.dp),
                                                )
                                                Text(
                                                    "›",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    modifier = Modifier.graphicsLayer(rotationZ = if (frequencyOpen) 90f else 0f),
                                                )
                                            }
                                        }

                                        if (frequencyOpen) {
                                            Column(
                                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Frequency.entries.forEach { option ->
                                                        TaskChip(option.label, frequency == option, accent) { chooseFrequency(option) }
                                                    }
                                                }
                                                if (frequency == Frequency.CUSTOM) {
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
                                                        RecurrenceUnit.entries.forEach { u ->
                                                            TaskChip(u.plural().replaceFirstChar { it.uppercase() }, recurrenceUnit == u, accent) { recurrenceUnit = u }
                                                        }
                                                    }
                                                }
                                                if (frequency == Frequency.MANUAL) {
                                                    Text(
                                                        "Comes back blank when completed, ready to re-date (e.g. Etsy order).",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                } else {
                                                    Text("Next date is counted", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        RecurrenceBase.entries.forEach { base ->
                                                            TaskChip(base.shortLabel(), recurrenceBase == base, accent) { recurrenceBase = base }
                                                        }
                                                    }
                                                    Text(
                                                        recurrenceBase.description(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }

                                        if (frequency != Frequency.MANUAL) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(accent.copy(alpha = 0.08f))
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                WizardIconGlyph(WizardGlyph.REPEAT, accent, Modifier.size(22.dp))
                                                Column {
                                                    Text("Next date will be based on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(recurrenceBase.shortLabel(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (effectiveLinksToPerson) {
                                SettingsCard {
                                    SettingRow(
                                        glyph = WizardGlyph.PERSON,
                                        title = "Assign to",
                                        description = "Link this task to a person",
                                    ) {
                                        Box {
                                            Text(
                                                selectedPerson?.name ?: "Not assigned",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { personMenuExpanded = true }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                            )
                                            DropdownMenu(expanded = personMenuExpanded, onDismissRequest = { personMenuExpanded = false }) {
                                                DropdownMenuItem(text = { Text("Not assigned") }, onClick = { selectedPerson = null; personMenuExpanded = false })
                                                people.forEach { person ->
                                                    DropdownMenuItem(text = { Text(person.name) }, onClick = { selectedPerson = person; personMenuExpanded = false })
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (effectiveLinksToEvent) {
                                SettingsCard {
                                    SettingRow(glyph = WizardGlyph.CALENDAR, title = "Linked event", description = "e.g. Christmas (optional)") {
                                        OutlinedTextField(
                                            value = linkedEvent,
                                            onValueChange = { linkedEvent = it },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            colors = fieldColors,
                                            modifier = Modifier.width(140.dp),
                                        )
                                    }
                                }
                            }

                            if (effectiveOrdered) {
                                SettingsCard {
                                    SettingRow(glyph = WizardGlyph.ORDER, title = "Order", description = "Where in the queue this goes") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TaskChip("Top", orderChoice == OrderChoice.TOP, accent) { orderChoice = OrderChoice.TOP }
                                            TaskChip("Bottom", orderChoice == OrderChoice.BOTTOM, accent) { orderChoice = OrderChoice.BOTTOM }
                                        }
                                    }
                                }
                            }

                            SettingsCard {
                                SettingRow(
                                    glyph = WizardGlyph.CHECK_CIRCLE,
                                    title = "Follow-up task",
                                    description = "Queue another task after this one",
                                ) {
                                    Switch(
                                        checked = showFollowUp,
                                        onCheckedChange = { showFollowUp = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = accent),
                                    )
                                }
                                if (showFollowUp) {
                                    Row(
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            Text(
                                                followUpTask?.name ?: "Choose a task",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { followUpMenuExpanded = true }
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                            )
                                            DropdownMenu(expanded = followUpMenuExpanded, onDismissRequest = { followUpMenuExpanded = false }) {
                                                // Scoped to this task's own category — a follow-up only
                                                // ever makes sense as a related task (subcategories are
                                                // themselves category-scoped, so matching one already
                                                // implies matching the category too), not any of
                                                // potentially dozens of unrelated tasks across every
                                                // category, which just made the picker unusable.
                                                existingTasks
                                                    .filter { it.id != editingTask?.id && it.taskCategoryId == selectedCategory?.id }
                                                    .forEach { candidate ->
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
                                            modifier = Modifier.width(112.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.height(4.dp))
                }

                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    AccentButton(
                        text = if (editingTask != null) "Save changes" else "Create task",
                        onClick = ::submit,
                        accent = accent,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        content = if (loading) {
                            { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskFormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        content()
    }
}

/** One category in the horizontal picker: tinted icon circle over its name. */
@Composable
private fun CategoryTile(category: TaskCategory, selected: Boolean, onClick: () -> Unit) {
    val accent = category.displayColor.toColorOrNull() ?: Color.Gray
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            CategoryIconGlyph(category.displayIcon, accent, Modifier.size(22.dp))
        }
        Text(
            category.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1,
        )
    }
}

/** A pill option — tinted with the category accent when selected, white with an outline otherwise. */
@Composable
private fun TaskChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.White)
            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) { content() }
}

@Composable
private fun SettingRow(
    glyph: WizardGlyph,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            WizardIconGlyph(glyph, MaterialTheme.colorScheme.onSurface, Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
internal fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
internal fun CloseButton(onClick: () -> Unit, glyph: String = "✕") {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
