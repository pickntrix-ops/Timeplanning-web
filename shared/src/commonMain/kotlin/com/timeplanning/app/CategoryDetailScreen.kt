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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fires a single-field category update (colour, a flag, ...) with shared busy/error/refresh plumbing. */
private fun updateFlag(
    scope: CoroutineScope,
    apiClient: ApiClient,
    sessionToken: String,
    categoryId: Long,
    request: UpdateTaskCategoryRequest,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit,
) {
    onBusy(true)
    scope.launch {
        runCatching { apiClient.updateTaskCategory(sessionToken, categoryId, request) }
            .onFailure { onError("Couldn't update category: ${it.message}") }
        onBusy(false)
        onDone()
    }
}

/**
 * Everything about one category in one place: its colour and default flags,
 * its subcategories (with their own resolved flags), and every real task
 * currently linked to it — reached by tapping a row on TaskCategoriesScreen.
 */
@Composable
fun CategoryDetailScreen(
    apiClient: ApiClient,
    sessionToken: String,
    categoryId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenTask: (Task, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf<TaskCategory?>(null) }
    var linkedTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddSubcategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        runCatching {
            val cats = apiClient.fetchTaskCategories(sessionToken)
            val tasks = apiClient.fetchTasks(sessionToken)
            cats to tasks
        }.onSuccess { (cats, tasks) ->
            category = cats.find { it.id == categoryId }
            linkedTasks = tasks.filter { it.taskCategoryId == categoryId }
        }.onFailure { error = "Couldn't load category: ${it.message}" }
        loading = false
    }

    fun refresh() { refreshKey++ }

    val current = category
    val accent = current?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize()) {
        // The header bleeds to the true screen edges (including under the status bar) —
        // only the buttons sitting on top of it need to clear the status bar themselves.
        Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(accent)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton("←", onClick = onBack)
                Box {
                    RoundIconButton("⋯", onClick = { menuExpanded = true })
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit category") },
                            onClick = {
                                menuExpanded = false
                                editingCategory = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete category") },
                            onClick = {
                                menuExpanded = false
                                busy = true
                                scope.launch {
                                    val result = runCatching { apiClient.deleteTaskCategory(sessionToken, categoryId) }
                                    result.onSuccess { onDeleted() }
                                    result.exceptionOrNull()?.let { error = it.serverMessage() }
                                    busy = false
                                }
                            },
                        )
                    }
                }
            }
        }

        if (loading && current == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        if (current != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-20).dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(current.name, style = MaterialTheme.typography.headlineSmall)

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }

                val flags = buildList {
                    if (current.ordered) add("Ordered")
                    if (current.linksToPerson) add("Links to person")
                    if (current.linksToEvent) add("Links to event")
                }
                if (flags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        flags.forEach { FlagBadge(it) }
                    }
                }

                Text(
                    current.defaultRecurrenceBase.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (editingCategory) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.08f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Edit category", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { editingCategory = false; showAddSubcategory = false }) { Text("Done") }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Colour", style = MaterialTheme.typography.labelLarge)
                            ColorSwatchPicker(
                                selected = current.displayColor,
                                onSelect = { hex ->
                                    busy = true
                                    scope.launch {
                                        runCatching { apiClient.updateTaskCategory(sessionToken, categoryId, UpdateTaskCategoryRequest(color = hex)) }
                                            .onFailure { error = "Couldn't update colour: ${it.message}" }
                                        busy = false
                                        refresh()
                                    }
                                },
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Settings", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectableChip("Ordered", current.ordered) {
                                    updateFlag(scope, apiClient, sessionToken, categoryId, UpdateTaskCategoryRequest(ordered = !current.ordered), onBusy = { busy = it }, onError = { error = it }, onDone = ::refresh)
                                }
                                SelectableChip("Links to person", current.linksToPerson) {
                                    updateFlag(scope, apiClient, sessionToken, categoryId, UpdateTaskCategoryRequest(linksToPerson = !current.linksToPerson), onBusy = { busy = it }, onError = { error = it }, onDone = ::refresh)
                                }
                                SelectableChip("Links to event", current.linksToEvent) {
                                    updateFlag(scope, apiClient, sessionToken, categoryId, UpdateTaskCategoryRequest(linksToEvent = !current.linksToEvent), onBusy = { busy = it }, onError = { error = it }, onDone = ::refresh)
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Subcategories", style = MaterialTheme.typography.labelLarge)
                            if (current.subcategories.isEmpty()) {
                                Text(
                                    "No subcategories yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            current.subcategories.forEach { sub ->
                                SubcategoryRow(
                                    sub = sub,
                                    category = current,
                                    accent = accent,
                                    busy = busy,
                                    editable = true,
                                    onDelete = {
                                        busy = true
                                        scope.launch {
                                            runCatching { apiClient.deleteTaskSubcategory(sessionToken, categoryId, sub.id) }
                                                .onFailure { error = "Couldn't delete subcategory: ${it.message}" }
                                            busy = false
                                            refresh()
                                        }
                                    },
                                )
                            }
                            TextButton(onClick = { showAddSubcategory = !showAddSubcategory }) {
                                Text(if (showAddSubcategory) "Cancel" else "+ Add subcategory")
                            }
                            if (showAddSubcategory) {
                                AddSubcategoryForm(
                                    category = current,
                                    busy = busy,
                                    onAdd = { request ->
                                        busy = true
                                        scope.launch {
                                            runCatching { apiClient.createTaskSubcategory(sessionToken, categoryId, request) }
                                                .onFailure { error = "Couldn't add subcategory: ${it.message}" }
                                            busy = false
                                            showAddSubcategory = false
                                            refresh()
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Subcategories", style = MaterialTheme.typography.titleSmall)
                        if (current.subcategories.isEmpty()) {
                            Text(
                                "No subcategories yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        current.subcategories.forEach { sub ->
                            SubcategoryRow(sub = sub, category = current, accent = accent, busy = busy, editable = false, onDelete = {})
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Linked tasks (${linkedTasks.size})", style = MaterialTheme.typography.titleSmall)
                    if (linkedTasks.isEmpty()) {
                        Text(
                            "No tasks use this category yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    linkedTasks.forEach { task ->
                        LinkedTaskRow(task = task, accent = accent, onClick = { onOpenTask(task, current.displayColor) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}

@Composable
private fun SubcategoryRow(sub: TaskSubcategory, category: TaskCategory, accent: Color, busy: Boolean, editable: Boolean, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${sub.name} (priority ${sub.priority})", style = MaterialTheme.typography.bodyMedium)
            if (editable) TextButton(onClick = onDelete, enabled = !busy) { Text("Remove") }
        }
        val resolvedFlags = buildList {
            if (sub.ordered ?: category.ordered) add("Ordered")
            if (sub.linksToPerson ?: category.linksToPerson) add("Links to person")
            if (sub.linksToEvent ?: category.linksToEvent) add("Links to event")
        }
        if (resolvedFlags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                resolvedFlags.forEach { FlagBadge(it) }
            }
        }
    }
}

@Composable
private fun LinkedTaskRow(task: Task, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(task.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(task.subcategoryName, task.dueDate ?: "No date").joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            task.status.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

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
                    ),
                )
            },
        ) { Text("Add subcategory") }
    }
}
