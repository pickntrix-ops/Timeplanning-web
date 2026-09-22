package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Scaffold
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

/**
 * User-defined replacement for the old fixed TaskType — a category (e.g.
 * "Cleaning", "Job", "Etsy Orders") with its own default settings, and
 * private subcategories that can each override those defaults (e.g.
 * "Project" defaults ordered=true, its "Etsy Prints" subcategory overrides
 * that back to false). This is just the flat list — tapping a category
 * opens CategoryDetailScreen for its colour/subcategories/linked tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCategoriesScreen(
    apiClient: ApiClient,
    sessionToken: String,
    currentTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    onOpenCategory: (TaskCategory) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showAddCategory by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BottomNavBar(current = currentTab, onSelect = onSelectTab) },
    ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Task Categories", style = MaterialTheme.typography.headlineSmall)
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

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                CategoryRow(category = category, onClick = { onOpenCategory(category) })
            }

            if (!loading && categories.isEmpty()) {
                item { Text("No categories yet. Add one above.") }
            }
        }
    }
    }
}

@Composable
private fun CategoryRow(category: TaskCategory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(category.displayColor.toColorOrNull() ?: Color.Gray))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleMedium)
            val subCount = category.subcategories.size
            Text(
                if (subCount == 0) category.defaultRecurrenceBase.label() else "$subCount subcategor${if (subCount == 1) "y" else "ies"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FlagBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun ColorSwatchPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CategoryPalette.forEach { hex ->
            val color = hex.toColorOrNull() ?: Color.Gray
            val isSelected = hex == selected
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(color)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier,
                    )
                    .clickable { onSelect(hex) },
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryForm(busy: Boolean, onAdd: (CreateTaskCategoryRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var recurrenceBase by remember { mutableStateOf(RecurrenceBase.DUE_DATE) }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    var ordered by remember { mutableStateOf(false) }
    var linksToPerson by remember { mutableStateOf(false) }
    var linksToEvent by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(CategoryPalette.first()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Category name") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorSwatchPicker(selected = color, onSelect = { color = it })

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
            onClick = { onAdd(CreateTaskCategoryRequest(name, recurrenceBase, ordered, linksToPerson, linksToEvent, color)) },
        ) { Text("Add category") }
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
internal fun TriStateRow(label: String, value: Boolean?, categoryDefault: Boolean, onChange: (Boolean?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip("Inherit (${if (categoryDefault) "on" else "off"})", value == null) { onChange(null) }
            SelectableChip("On", value == true) { onChange(true) }
            SelectableChip("Off", value == false) { onChange(false) }
        }
    }
}
