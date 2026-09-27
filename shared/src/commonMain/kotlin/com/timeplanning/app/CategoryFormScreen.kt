package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Create AND edit a category in one screen — same shape as AddTaskScreen's
 * editingTask param. Either way, subcategories (add/remove, plus each one's
 * own overrides) are managed right here too: when editing, they're created
 * immediately via the API; when creating a brand-new category (no id yet to
 * attach them to), they're staged locally and created right after the
 * category itself is, on submit.
 */
@Composable
fun CategoryFormScreen(
    apiClient: ApiClient,
    sessionToken: String,
    editingCategory: TaskCategory? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(editingCategory?.name ?: "") }
    var description by remember { mutableStateOf(editingCategory?.description ?: "") }
    var icon by remember { mutableStateOf(editingCategory?.displayIcon ?: CategoryIcon.default) }
    var color by remember { mutableStateOf(editingCategory?.displayColor ?: CategoryPalette.first()) }
    var recurrenceBase by remember { mutableStateOf(editingCategory?.defaultRecurrenceBase ?: RecurrenceBase.DUE_DATE) }
    var ordered by remember { mutableStateOf(editingCategory?.ordered ?: false) }
    var linksToPerson by remember { mutableStateOf(editingCategory?.linksToPerson ?: false) }
    var linksToEvent by remember { mutableStateOf(editingCategory?.linksToEvent ?: false) }
    var energyLevel by remember { mutableStateOf(editingCategory?.energyLevel) }
    var suggestingEnergy by remember { mutableStateOf(false) }
    var autoSchedule by remember { mutableStateOf(editingCategory?.autoSchedule ?: true) }
    var weeklyHourCap by remember { mutableStateOf(editingCategory?.weeklyHourCapMinutes?.minutesToHoursText() ?: "") }

    var subcategories by remember { mutableStateOf(editingCategory?.subcategories ?: emptyList()) }
    // Only used when creating (editingCategory == null) — there's no category id yet to attach
    // subcategories to, so they're staged here and created right after the category itself is.
    var pendingSubcategories by remember { mutableStateOf(listOf<CreateTaskSubcategoryRequest>()) }
    var showAddSubcategory by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun suggestEnergy() {
        if (name.isBlank()) return
        suggestingEnergy = true
        error = null
        scope.launch {
            runCatching { apiClient.suggestCategoryEnergyLevel(sessionToken, name, description.ifBlank { null }) }
                .onSuccess { suggestion -> if (suggestion != null) energyLevel = suggestion else error = "Couldn't come up with a suggestion — pick one yourself." }
                .onFailure { error = it.serverMessage() }
            suggestingEnergy = false
        }
    }

    fun refreshSubcategories() {
        val id = editingCategory?.id ?: return
        scope.launch {
            runCatching { apiClient.fetchTaskCategories(sessionToken) }
                .onSuccess { cats -> cats.find { it.id == id }?.let { subcategories = it.subcategories } }
        }
    }

    fun submit() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                if (editingCategory != null) {
                    apiClient.updateTaskCategory(
                        sessionToken,
                        editingCategory.id,
                        UpdateTaskCategoryRequest(
                            name = name,
                            defaultRecurrenceBase = recurrenceBase,
                            ordered = ordered,
                            linksToPerson = linksToPerson,
                            linksToEvent = linksToEvent,
                            color = color,
                            icon = icon.name,
                            clearDescription = description.isBlank(),
                            description = description.ifBlank { null },
                            clearEnergyLevel = energyLevel == null,
                            energyLevel = energyLevel,
                            clearWeeklyHourCapMinutes = weeklyHourCap.hoursTextToMinutes() == null,
                            weeklyHourCapMinutes = weeklyHourCap.hoursTextToMinutes(),
                            autoSchedule = autoSchedule,
                        ),
                    )
                } else {
                    val created = apiClient.createTaskCategory(
                        sessionToken,
                        CreateTaskCategoryRequest(
                            name = name,
                            defaultRecurrenceBase = recurrenceBase,
                            ordered = ordered,
                            linksToPerson = linksToPerson,
                            linksToEvent = linksToEvent,
                            color = color,
                            icon = icon.name,
                            description = description.ifBlank { null },
                            energyLevel = energyLevel,
                            weeklyHourCapMinutes = weeklyHourCap.hoursTextToMinutes(),
                            autoSchedule = autoSchedule,
                        ),
                    )
                    pendingSubcategories.forEach { request ->
                        apiClient.createTaskSubcategory(sessionToken, created.id, request)
                    }
                }
            }.onSuccess { onDone() }
                .onFailure { error = it.serverMessage() }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (editingCategory != null) "Edit category" else "New category", style = MaterialTheme.typography.headlineSmall)
            CloseButton(onClick = onCancel)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormSection("Name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Cleaning, Life Admin") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection("Description (optional)") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("What kinds of tasks live in this category?") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection("Icon") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryIcon.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { ic -> IconTile(ic, selected = icon == ic, onClick = { icon = ic }) }
                        }
                    }
                }
            }

            FormSection("Colour") {
                ColorSwatchPicker(selected = color, onSelect = { color = it })
            }

            FormSection("Settings") {
                Text(
                    "Turn on what applies to tasks in this category by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip("Done in order", ordered) { ordered = !ordered }
                    SelectableChip("Links to a person", linksToPerson) { linksToPerson = !linksToPerson }
                    SelectableChip("Links to an event", linksToEvent) { linksToEvent = !linksToEvent }
                }
            }

            FormSection("Energy level") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Not required — used later to plan when in the day this category's tasks fit best.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = ::suggestEnergy, enabled = name.isNotBlank() && !suggestingEnergy) {
                        if (suggestingEnergy) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text("✨ Suggest")
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip("Any", energyLevel == null) { energyLevel = null }
                    EnergyLevel.entries.forEach { level -> SelectableChip(level.label(), energyLevel == level) { energyLevel = level } }
                }
            }

            FormSection("Weekly planning") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Plan this into my week automatically", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (autoSchedule) "Tasks here can be auto-scheduled, up to any cap below." else "You'll add tasks/hours from this category yourself. Daily tasks and tasks with a due date are still always scheduled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(checked = autoSchedule, onCheckedChange = { autoSchedule = it })
                }
                if (autoSchedule) {
                    OutlinedTextField(
                        value = weeklyHourCap,
                        onValueChange = { input -> weeklyHourCap = input.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Weekly cap (hours)") },
                        placeholder = { Text("No cap") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Optional — stops this category from taking up the whole week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FormSection("When a repeating task here is completed") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceBase.entries.forEach { base ->
                        SelectableChip(base.shortLabel(), recurrenceBase == base) { recurrenceBase = base }
                    }
                }
                Text(
                    recurrenceBase.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FormSection("Subcategories") {
                Text(
                    "Each subcategory can override any of the settings above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (editingCategory != null) {
                    if (subcategories.isEmpty()) {
                        Text(
                            "No subcategories yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    subcategories.forEach { sub ->
                        EditableSubcategoryRow(
                            sub = sub,
                            category = editingCategory.copy(ordered = ordered, linksToPerson = linksToPerson, linksToEvent = linksToEvent, defaultRecurrenceBase = recurrenceBase),
                            busy = busy,
                            onDelete = {
                                busy = true
                                scope.launch {
                                    runCatching { apiClient.deleteTaskSubcategory(sessionToken, editingCategory.id, sub.id) }
                                        .onFailure { error = it.serverMessage() }
                                    busy = false
                                    refreshSubcategories()
                                }
                            },
                        )
                    }
                } else {
                    if (pendingSubcategories.isEmpty()) {
                        Text(
                            "No subcategories yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    pendingSubcategories.forEachIndexed { index, request ->
                        PendingSubcategoryRow(
                            request = request,
                            categoryOrdered = ordered,
                            categoryLinksToPerson = linksToPerson,
                            categoryLinksToEvent = linksToEvent,
                            categoryRecurrenceBase = recurrenceBase,
                            accent = color.toColorOrNull() ?: MaterialTheme.colorScheme.primary,
                            onDelete = { pendingSubcategories = pendingSubcategories.filterIndexed { i, _ -> i != index } },
                        )
                    }
                }
                TextButton(onClick = { showAddSubcategory = !showAddSubcategory }) {
                    Text(if (showAddSubcategory) "Cancel" else "+ Add subcategory")
                }
                if (showAddSubcategory) {
                    AddSubcategoryForm(
                        categoryOrdered = ordered,
                        categoryLinksToPerson = linksToPerson,
                        categoryLinksToEvent = linksToEvent,
                        categoryRecurrenceBase = recurrenceBase,
                        busy = busy,
                        onAdd = { request ->
                            if (editingCategory != null) {
                                busy = true
                                scope.launch {
                                    runCatching { apiClient.createTaskSubcategory(sessionToken, editingCategory.id, request) }
                                        .onFailure { error = it.serverMessage() }
                                    busy = false
                                    showAddSubcategory = false
                                    refreshSubcategories()
                                }
                            } else {
                                pendingSubcategories = pendingSubcategories + request
                                showAddSubcategory = false
                            }
                        },
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Box(modifier = Modifier.height(4.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Button(
                enabled = name.isNotBlank() && !loading,
                onClick = ::submit,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(if (editingCategory != null) "Save changes" else "Create category", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun EditableSubcategoryRow(sub: TaskSubcategory, category: TaskCategory, busy: Boolean, onDelete: () -> Unit) {
    val accent = category.displayColor.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sub.name, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDelete, enabled = !busy) { Text("Remove") }
        }
        val resolvedFlags = buildList {
            if (sub.ordered ?: category.ordered) add("Done in order")
            if (sub.linksToPerson ?: category.linksToPerson) add("Links to a person")
            if (sub.linksToEvent ?: category.linksToEvent) add("Links to an event")
            add(sub.effectiveRecurrenceBase(category).shortLabel())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            resolvedFlags.forEach { FlagBadge(it) }
        }
    }
}

/** A staged-but-not-yet-created subcategory, shown while creating a brand-new category (no id to attach it to yet). */
@Composable
private fun PendingSubcategoryRow(
    request: CreateTaskSubcategoryRequest,
    categoryOrdered: Boolean,
    categoryLinksToPerson: Boolean,
    categoryLinksToEvent: Boolean,
    categoryRecurrenceBase: RecurrenceBase,
    accent: Color,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(request.name, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDelete) { Text("Remove") }
        }
        val resolvedFlags = buildList {
            if (request.ordered ?: categoryOrdered) add("Done in order")
            if (request.linksToPerson ?: categoryLinksToPerson) add("Links to a person")
            if (request.linksToEvent ?: categoryLinksToEvent) add("Links to an event")
            add((request.recurrenceBase ?: categoryRecurrenceBase).shortLabel())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            resolvedFlags.forEach { FlagBadge(it) }
        }
    }
}

@Composable
internal fun AddSubcategoryForm(
    categoryOrdered: Boolean,
    categoryLinksToPerson: Boolean,
    categoryLinksToEvent: Boolean,
    categoryRecurrenceBase: RecurrenceBase,
    busy: Boolean,
    onAdd: (CreateTaskSubcategoryRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ordered by remember { mutableStateOf<Boolean?>(null) }
    var linksToPerson by remember { mutableStateOf<Boolean?>(null) }
    var linksToEvent by remember { mutableStateOf<Boolean?>(null) }
    var recurrenceBase by remember { mutableStateOf<RecurrenceBase?>(null) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Subcategory name") },
            modifier = Modifier.fillMaxWidth(),
        )

        TriStateRow("Done in order", ordered, categoryOrdered) { ordered = it }
        TriStateRow("Links to a person", linksToPerson, categoryLinksToPerson) { linksToPerson = it }
        TriStateRow("Links to an event", linksToEvent, categoryLinksToEvent) { linksToEvent = it }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("When a repeating task here is completed", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectableChip("Inherit (${categoryRecurrenceBase.shortLabel()})", recurrenceBase == null) { recurrenceBase = null }
                RecurrenceBase.entries.forEach { base ->
                    SelectableChip(base.shortLabel(), recurrenceBase == base) { recurrenceBase = base }
                }
            }
        }

        Button(
            enabled = name.isNotBlank() && !busy,
            onClick = {
                onAdd(
                    CreateTaskSubcategoryRequest(
                        name = name,
                        ordered = ordered,
                        linksToPerson = linksToPerson,
                        linksToEvent = linksToEvent,
                        recurrenceBase = recurrenceBase,
                    ),
                )
            },
        ) { Text("Add subcategory") }
    }
}
