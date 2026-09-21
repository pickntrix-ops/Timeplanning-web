package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * User-defined replacement for the old fixed TaskType — a category (e.g.
 * "Cleaning", "Job", "Etsy Orders") with its own default settings, and
 * private subcategories that can each override those defaults (e.g.
 * "Project" defaults ordered=true, its "Etsy Prints" subcategory overrides
 * that back to false).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCategoriesScreen(apiClient: ApiClient, sessionToken: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showAddCategory by remember { mutableStateOf(false) }
    var expandedCategoryId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching { apiClient.fetchTaskCategories(sessionToken) }
            .onSuccess { categories = it }
            .onFailure { error = "Couldn't load categories: ${it.message}" }
        loading = false
    }

    fun refresh() { refreshKey++ }

    Column(modifier = Modifier.safeContentPadding().fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Task Categories", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
        }

        if (loading) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Your categories", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showAddCategory = !showAddCategory }) {
                Text(if (showAddCategory) "Cancel" else "+ New category")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showAddCategory) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            AddCategoryForm(
                                busy = busy,
                                onAdd = { request ->
                                    busy = true
                                    scope.launch {
                                        runCatching { apiClient.createTaskCategory(sessionToken, request) }
                                            .onSuccess { showAddCategory = false }
                                            .onFailure { error = "Couldn't add category: ${it.message}" }
                                        busy = false
                                        refresh()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            items(categories) { category ->
                CategoryCard(
                    category = category,
                    expanded = expandedCategoryId == category.id,
                    busy = busy,
                    onToggleExpanded = { expandedCategoryId = if (expandedCategoryId == category.id) null else category.id },
                    onDeleteCategory = {
                        busy = true
                        scope.launch {
                            runCatching { apiClient.deleteTaskCategory(sessionToken, category.id) }
                                .onFailure { error = "Couldn't delete category: ${it.message}" }
                            busy = false
                            refresh()
                        }
                    },
                    onAddSubcategory = { request ->
                        busy = true
                        scope.launch {
                            runCatching { apiClient.createTaskSubcategory(sessionToken, category.id, request) }
                                .onFailure { error = "Couldn't add subcategory: ${it.message}" }
                            busy = false
                            refresh()
                        }
                    },
                    onDeleteSubcategory = { subcategoryId ->
                        busy = true
                        scope.launch {
                            runCatching { apiClient.deleteTaskSubcategory(sessionToken, category.id, subcategoryId) }
                                .onFailure { error = "Couldn't delete subcategory: ${it.message}" }
                            busy = false
                            refresh()
                        }
                    },
                )
            }

            if (!loading && categories.isEmpty()) {
                item { Text("No categories yet. Add one above.") }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: TaskCategory,
    expanded: Boolean,
    busy: Boolean,
    onToggleExpanded: () -> Unit,
    onDeleteCategory: () -> Unit,
    onAddSubcategory: (CreateTaskSubcategoryRequest) -> Unit,
    onDeleteSubcategory: (Long) -> Unit,
) {
    var showAddSubcategory by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                    Text(categorySummary(category), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDeleteCategory, enabled = !busy) { Text("Delete") }
            }

            if (expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    category.subcategories.forEach { sub ->
                        SubcategoryRow(sub, category, busy, onDelete = { onDeleteSubcategory(sub.id) })
                    }

                    TextButton(onClick = { showAddSubcategory = !showAddSubcategory }) {
                        Text(if (showAddSubcategory) "Cancel" else "+ Add subcategory")
                    }

                    if (showAddSubcategory) {
                        AddSubcategoryForm(
                            category = category,
                            busy = busy,
                            onAdd = { request -> onAddSubcategory(request); showAddSubcategory = false },
                        )
                    }
                }
            }
        }
    }
}

private fun categorySummary(category: TaskCategory): String {
    val flags = buildList {
        if (category.ordered) add("ordered")
        if (category.linksToPerson) add("links to person")
        if (category.linksToEvent) add("links to event")
    }
    val flagText = if (flags.isEmpty()) "no defaults set" else flags.joinToString(", ")
    return "${category.defaultRecurrenceBase.label()} · $flagText"
}

@Composable
private fun SubcategoryRow(sub: TaskSubcategory, category: TaskCategory, busy: Boolean, onDelete: () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${sub.name} (priority ${sub.priority})", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDelete, enabled = !busy) { Text("Remove") }
        }
        Text(
            "Ordered: ${overrideLabel(sub.ordered, category.ordered)} · " +
                "Person: ${overrideLabel(sub.linksToPerson, category.linksToPerson)} · " +
                "Event: ${overrideLabel(sub.linksToEvent, category.linksToEvent)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun overrideLabel(override: Boolean?, categoryDefault: Boolean): String =
    if (override == null) "inherit (${if (categoryDefault) "on" else "off"})" else if (override) "on" else "off"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryForm(busy: Boolean, onAdd: (CreateTaskCategoryRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var recurrenceBase by remember { mutableStateOf(RecurrenceBase.DUE_DATE) }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    var ordered by remember { mutableStateOf(false) }
    var linksToPerson by remember { mutableStateOf(false) }
    var linksToEvent by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Category name") },
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(expanded = recurrenceMenuExpanded, onExpandedChange = { recurrenceMenuExpanded = it }) {
            OutlinedTextField(
                value = recurrenceBase.label(),
                onValueChange = {},
                readOnly = true,
                label = { Text("When a repeating task here is completed") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = recurrenceMenuExpanded, onDismissRequest = { recurrenceMenuExpanded = false }) {
                RecurrenceBase.entries.forEach { base ->
                    DropdownMenuItem(text = { Text(base.label()) }, onClick = { recurrenceBase = base; recurrenceMenuExpanded = false })
                }
            }
        }

        CheckboxRow("Worked in order (a strict sequence)", ordered) { ordered = it }
        CheckboxRow("Usually links to a person", linksToPerson) { linksToPerson = it }
        CheckboxRow("Usually links to an event", linksToEvent) { linksToEvent = it }

        Button(
            enabled = name.isNotBlank() && !busy,
            onClick = { onAdd(CreateTaskCategoryRequest(name, recurrenceBase, ordered, linksToPerson, linksToEvent)) },
        ) { Text("Add category") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubcategoryForm(category: TaskCategory, busy: Boolean, onAdd: (CreateTaskSubcategoryRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("0") }
    var ordered by remember { mutableStateOf<Boolean?>(null) }
    var linksToPerson by remember { mutableStateOf<Boolean?>(null) }
    var linksToEvent by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Subcategory name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = priority,
            onValueChange = { priority = it.filter { c -> c.isDigit() } },
            label = { Text("Priority (lower goes first)") },
            modifier = Modifier.fillMaxWidth(),
        )

        TriStateRow("Worked in order", ordered, category.ordered) { ordered = it }
        TriStateRow("Links to a person", linksToPerson, category.linksToPerson) { linksToPerson = it }
        TriStateRow("Links to an event", linksToEvent, category.linksToEvent) { linksToEvent = it }

        Button(
            enabled = name.isNotBlank() && !busy,
            onClick = {
                onAdd(
                    CreateTaskSubcategoryRequest(
                        name = name,
                        priority = priority.toIntOrNull() ?: 0,
                        ordered = ordered,
                        linksToPerson = linksToPerson,
                        linksToEvent = linksToEvent,
                    )
                )
            },
        ) { Text("Add subcategory") }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

/** A 3-way inherit/on/off control — null means "inherit the category's default", shown alongside for context. */
@Composable
private fun TriStateRow(label: String, value: Boolean?, categoryDefault: Boolean, onChange: (Boolean?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriStateChip("Inherit (${if (categoryDefault) "on" else "off"})", value == null) { onChange(null) }
            TriStateChip("On", value == true) { onChange(true) }
            TriStateChip("Off", value == false) { onChange(false) }
        }
    }
}

@Composable
private fun TriStateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
