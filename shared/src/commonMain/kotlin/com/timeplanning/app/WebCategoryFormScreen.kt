package com.timeplanning.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Web's create/edit category page — same data/logic shape as
 * CategoryFormScreen (one screen handles both, subcategories staged locally
 * until the category itself exists), but genuinely desktop UI: a wide
 * WebPageShell card instead of a full-screen phone form.
 */
@Composable
fun WebCategoryFormScreen(
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
    var pendingSubcategories by remember { mutableStateOf(listOf<CreateTaskSubcategoryRequest>()) }
    var showAddSubcategory by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val accent = color.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val fieldColors = webFieldColors(accent)

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
                    pendingSubcategories.forEach { request -> apiClient.createTaskSubcategory(sessionToken, created.id, request) }
                }
            }.onSuccess { onDone() }
                .onFailure { error = it.serverMessage() }
            loading = false
        }
    }

    WebPageShell(
        title = if (editingCategory != null) "Edit category" else "New category",
        onClose = onCancel,
        accent = accent,
        actions = {
            AccentButton(
                text = if (editingCategory != null) "Save changes" else "Create category",
                onClick = ::submit,
                accent = accent,
                enabled = name.isNotBlank() && !loading,
                modifier = Modifier.height(44.dp),
                content = if (loading) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
                } else null,
            )
        },
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebSectionLabel("Name")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Cleaning, Life Admin") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebSectionLabel("Description (optional)")
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("What kinds of tasks live in this category?") },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WebSectionLabel("When a repeating task here is completed")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceBase.entries.forEach { base -> WebChip(base.shortLabel(), recurrenceBase == base, accent) { recurrenceBase = base } }
                    }
                    Text(recurrenceBase.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WebSectionLabel("Icon")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryIcon.entries.forEach { ic -> IconTile(ic, selected = icon == ic, onClick = { icon = ic }, accent = accent, modifier = Modifier.size(52.dp)) }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WebSectionLabel("Colour")
                    ColorSwatchPicker(selected = color, onSelect = { color = it })
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WebSectionLabel("Settings")
                    Text(
                        "Turn on what applies to tasks in this category by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WebChip("Done in order", ordered, accent) { ordered = !ordered }
                        WebChip("Links to a person", linksToPerson, accent) { linksToPerson = !linksToPerson }
                        WebChip("Links to an event", linksToEvent, accent) { linksToEvent = !linksToEvent }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        WebSectionLabel("Energy level")
                        Row(
                            modifier = Modifier
                                .background(if (suggestingEnergy) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .then(if (name.isNotBlank() && !suggestingEnergy) Modifier.clickable { suggestEnergy() } else Modifier)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (suggestingEnergy) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = accent, strokeWidth = 2.dp)
                            }
                            Text(
                                "✨ Suggest",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (name.isNotBlank()) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "Not required — used later to plan when in the day this category's tasks fit best.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WebChip("Any", energyLevel == null, accent) { energyLevel = null }
                        EnergyLevel.entries.forEach { level -> WebChip(level.label(), energyLevel == level, accent) { energyLevel = level } }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            WebSectionLabel("Plan this into my week automatically")
                            Text(
                                if (autoSchedule) "Tasks here can be auto-scheduled, up to any cap below." else "You'll add tasks/hours from this category yourself. Daily tasks and tasks with a due date are still always scheduled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = autoSchedule, onCheckedChange = { autoSchedule = it }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                    }
                    if (autoSchedule) {
                        OutlinedTextField(
                            value = weeklyHourCap,
                            onValueChange = { input -> weeklyHourCap = input.filter { c -> c.isDigit() || c == '.' } },
                            placeholder = { Text("No cap") },
                            label = { Text("Weekly cap (hours)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.width(400.dp),
                        )
                        Text(
                            "Optional — stops this category from taking up the whole week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WebSectionLabel("Subcategories")
            Text(
                "Each subcategory can override any of the settings above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val liveCategory = editingCategory?.copy(ordered = ordered, linksToPerson = linksToPerson, linksToEvent = linksToEvent, defaultRecurrenceBase = recurrenceBase)

            if (editingCategory != null) {
                if (subcategories.isEmpty()) {
                    Text("No subcategories yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                subcategories.forEach { sub ->
                    WebSubcategoryRow(
                        name = sub.name,
                        accent = accent,
                        flags = buildList {
                            if (sub.ordered ?: ordered) add("Done in order")
                            if (sub.linksToPerson ?: linksToPerson) add("Links to a person")
                            if (sub.linksToEvent ?: linksToEvent) add("Links to an event")
                            add(sub.effectiveRecurrenceBase(liveCategory!!).shortLabel())
                        },
                        busy = busy,
                        onDelete = {
                            busy = true
                            scope.launch {
                                runCatching { apiClient.deleteTaskSubcategory(sessionToken, editingCategory.id, sub.id) }.onFailure { error = it.serverMessage() }
                                busy = false
                                refreshSubcategories()
                            }
                        },
                    )
                }
            } else {
                if (pendingSubcategories.isEmpty()) {
                    Text("No subcategories yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                pendingSubcategories.forEachIndexed { index, request ->
                    WebSubcategoryRow(
                        name = request.name,
                        accent = accent,
                        flags = buildList {
                            if (request.ordered ?: ordered) add("Done in order")
                            if (request.linksToPerson ?: linksToPerson) add("Links to a person")
                            if (request.linksToEvent ?: linksToEvent) add("Links to an event")
                            add((request.recurrenceBase ?: recurrenceBase).shortLabel())
                        },
                        busy = false,
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
                                runCatching { apiClient.createTaskSubcategory(sessionToken, editingCategory.id, request) }.onFailure { error = it.serverMessage() }
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
    }
}

@Composable
private fun WebSubcategoryRow(name: String, accent: Color, flags: List<String>, busy: Boolean, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            TextButton(onClick = onDelete, enabled = !busy) { Text("Remove") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            flags.forEach { FlagBadge(it) }
        }
    }
}
