package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * The desktop-web categories area: a persistent master-detail shell, NOT the
 * mobile dome-header screens squeezed into a centered phone panel. Left
 * sidebar lists every category (with this-week progress); the right pane
 * shows whichever of CategoryDetail/CategoryTasks `screen` currently is,
 * without remounting the sidebar. Create/edit forms and task detail still
 * navigate away to their own WebPageShell-based pages (see App.kt) — only
 * the browsing loop lives in this persistent shell.
 */
@Composable
fun WebCategoriesScreen(
    apiClient: ApiClient,
    sessionToken: String,
    screen: Screen,
    currentTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    onNavigate: (Screen) -> Unit,
    refreshKey: Int,
) {
    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var localRefresh by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, localRefresh) {
        loading = true
        error = null
        runCatching { apiClient.fetchTaskCategories(sessionToken) to apiClient.fetchTasks(sessionToken) }
            .onSuccess { (cats, allTasks) -> categories = cats; tasks = allTasks }
            .onFailure { error = "Couldn't load categories: ${it.message}" }
        loading = false
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val weekStart = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val weekEnd = remember(weekStart) { weekStart.plus(6, DateTimeUnit.DAY) }

    val selectedCategoryId = when (screen) {
        is Screen.CategoryDetail -> screen.categoryId
        is Screen.CategoryTasks -> screen.categoryId
        is Screen.BulkAddTasks -> screen.categoryId
        else -> null
    }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    // The Tasks page itself: full-width lists (this week / all tasks / history), no category side panel.
    // Category pages (reached from the app sidebar's category shortcuts) keep the panel for editing.
    if (screen == Screen.TaskCategories) {
        Box(Modifier.fillMaxSize().background(WebColorScheme.background), contentAlignment = Alignment.TopCenter) {
            TasksOverview(
                apiClient = apiClient,
                sessionToken = sessionToken,
                allTasks = tasks,
                categoryColors = categories.associate { it.id to it.displayColor },
                onOpenTask = { task -> onNavigate(Screen.TaskDetail(task, categories.find { it.id == task.taskCategoryId }?.displayColor)) },
                onTasksChanged = { localRefresh++ },
                horizontalPadding = 32.dp,
                modifier = Modifier.padding(top = 24.dp).widthIn(max = 900.dp),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(WebColorScheme.background)) {
        WebTopNav(currentTab, onSelectTab)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            WebCategorySidebar(
                categories = categories,
                tasks = tasks,
                weekStart = weekStart,
                weekEnd = weekEnd,
                selectedCategoryId = selectedCategoryId,
                onSelect = { onNavigate(Screen.CategoryDetail(it.id)) },
                onAddCategory = { onNavigate(Screen.AddCategory) },
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                }
                when {
                    loading && categories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    selectedCategory != null && screen is Screen.BulkAddTasks -> WebBulkAddTasksPane(
                        apiClient = apiClient,
                        sessionToken = sessionToken,
                        category = selectedCategory,
                        subcategoryId = screen.subcategoryId,
                        allTasks = tasks,
                        onBack = {
                            onNavigate(
                                if (screen.subcategoryId != null) Screen.CategoryTasks(selectedCategory.id, screen.subcategoryId)
                                else Screen.CategoryDetail(selectedCategory.id),
                            )
                        },
                        onRefresh = { localRefresh++ },
                    )
                    selectedCategory != null && screen is Screen.CategoryTasks -> WebSubcategoryTasksPane(
                        apiClient = apiClient,
                        sessionToken = sessionToken,
                        category = selectedCategory,
                        subcategoryId = screen.subcategoryId,
                        allTasks = tasks,
                        today = today,
                        onBack = { onNavigate(Screen.CategoryDetail(selectedCategory.id)) },
                        onOpenTask = { task -> onNavigate(Screen.TaskDetail(task, selectedCategory.displayColor)) },
                        onRefresh = { localRefresh++ },
                        onBulkAdd = { onNavigate(Screen.BulkAddTasks(selectedCategory.id, screen.subcategoryId)) },
                        onCopied = { newSubId -> onNavigate(Screen.CategoryTasks(selectedCategory.id, newSubId)) },
                    )
                    selectedCategory != null -> WebCategoryDetailPane(
                        apiClient = apiClient,
                        sessionToken = sessionToken,
                        category = selectedCategory,
                        allTasks = tasks,
                        today = today,
                        weekStart = weekStart,
                        weekEnd = weekEnd,
                        onEdit = { onNavigate(Screen.EditCategory(selectedCategory)) },
                        onDeleted = { localRefresh++; onNavigate(Screen.TaskCategories) },
                        onViewTasks = { subId -> onNavigate(Screen.CategoryTasks(selectedCategory.id, subId)) },
                        onOpenTask = { task -> onNavigate(Screen.TaskDetail(task, selectedCategory.displayColor)) },
                        onRefresh = { localRefresh++ },
                        onBulkAdd = { onNavigate(Screen.BulkAddTasks(selectedCategory.id, null)) },
                    )
                    !loading && categories.isEmpty() -> WebEmptyPane("No categories yet — create your first one from the sidebar.")
                    // No category picked: this week's tasks, search-and-tick, and history (see TasksOverview).
                    else -> TasksOverview(
                        apiClient = apiClient,
                        sessionToken = sessionToken,
                        allTasks = tasks,
                        categoryColors = categories.associate { it.id to it.displayColor },
                        onOpenTask = { task -> onNavigate(Screen.TaskDetail(task, categories.find { it.id == task.taskCategoryId }?.displayColor)) },
                        onTasksChanged = { localRefresh++ },
                        horizontalPadding = 32.dp,
                        modifier = Modifier.padding(top = 20.dp).widthIn(max = 900.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WebCategorySidebar(
    categories: List<TaskCategory>,
    tasks: List<Task>,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    selectedCategoryId: Long?,
    onSelect: (TaskCategory) -> Unit,
    onAddCategory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color.White)
            .border(0.dp, Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Categories",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        categories.forEach { category ->
            val accent = category.displayColor.toColorOrNull() ?: Color.Gray
            val inWeek = tasks.filter {
                it.taskCategoryId == category.id && it.status != TaskStatus.ABANDONED &&
                    it.dueDate != null && LocalDate.parse(it.dueDate) in weekStart..weekEnd
            }
            val selected = category.id == selectedCategoryId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(category) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    CategoryIconGlyph(category.displayIcon, accent, Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1,
                    )
                    Text(
                        "${inWeek.count { it.status == TaskStatus.COMPLETED }}/${inWeek.size} this week",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onAddCategory)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WizardIconGlyph(WizardGlyph.PLUS, MaterialTheme.colorScheme.onSurface, Modifier.size(16.dp))
            Text("New category", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun WebEmptyPane(message: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The right pane for Screen.CategoryDetail — an overview of one category: header, subcategory grid, this-week tasks. Flat/desktop styling, no dome. */
@Composable
private fun WebCategoryDetailPane(
    apiClient: ApiClient,
    sessionToken: String,
    category: TaskCategory,
    allTasks: List<Task>,
    today: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onViewTasks: (Long?) -> Unit,
    onOpenTask: (Task) -> Unit,
    onRefresh: () -> Unit,
    onBulkAdd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var error by remember(category.id) { mutableStateOf<String?>(null) }
    val accent = category.displayColor.toColorOrNull() ?: MaterialTheme.colorScheme.primary

    val categoryTasks = allTasks.filter { it.taskCategoryId == category.id && it.status != TaskStatus.ABANDONED }
    val tasksThisWeek = categoryTasks.filter { it.dueDate != null && LocalDate.parse(it.dueDate) in weekStart..weekEnd }
    val openThisWeek = tasksThisWeek.filter { it.status != TaskStatus.COMPLETED }.sortedBy { it.dueDate }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    CategoryIconGlyph(category.displayIcon, accent, Modifier.size(26.dp))
                }
                Column {
                    Text(category.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    if (!category.description.isNullOrBlank()) {
                        Text(category.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WebChip("Edit", selected = false, accent = accent, onClick = onEdit)
                WebChip("Delete", selected = false, accent = MaterialTheme.colorScheme.error) {
                    scope.launch {
                        val result = runCatching { apiClient.deleteTaskCategory(sessionToken, category.id) }
                        result.onSuccess { onDeleted() }
                        result.exceptionOrNull()?.let { error = it.serverMessage() }
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Subcategories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text("${category.subcategories.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (category.subcategories.isEmpty()) {
            Text("No subcategories yet — add some from Edit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier.fillMaxWidth().height((160 * ((category.subcategories.size + 2) / 3).coerceAtLeast(1)).dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(category.subcategories) { sub ->
                    val planned = tasksThisWeek.filter { it.subcategoryId == sub.id }
                    WebSubcategoryCard(
                        name = sub.name,
                        planned = planned.size,
                        completed = planned.count { it.status == TaskStatus.COMPLETED },
                        onClick = { onViewTasks(sub.id) },
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "This week · ${openThisWeek.size} task${if (openThisWeek.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                WebChip("+ Add tasks", selected = false, accent = accent, onClick = onBulkAdd)
                Text(
                    "View all ›",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onViewTasks(null) }.padding(vertical = 4.dp),
                )
            }
        }

        if (openThisWeek.isEmpty()) {
            Text("Nothing due this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WebCard(modifier = Modifier.fillMaxWidth()) {
                openThisWeek.forEachIndexed { index, task ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    WebTaskRow(
                        task = task,
                        accent = accent,
                        subtitle = { Text(dueLabel(task.dueDate, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onOpen = { onOpenTask(task) },
                        onComplete = { scope.launch { runCatching { apiClient.completeTask(sessionToken, task.id) }; onRefresh() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSubcategoryCard(name: String, planned: Int, completed: Int, onClick: () -> Unit) {
    val percent = if (planned == 0) 0f else completed.toFloat() / planned
    WebCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (planned == 0) "No tasks planned" else "$planned planned this week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(percent.coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(Color(0xFF2E9E6B)))
            }
        }
    }
}

private enum class WebTaskSort(val label: String) { DEFAULT("Default"), DUE("Due date"), NAME("Name") }

private data class WebRecurrenceChip(val label: String, val background: Color, val content: Color)

private fun webRecurrenceChip(task: Task): WebRecurrenceChip? {
    val n = task.recurrenceInterval ?: return null
    val unit = task.recurrenceUnit ?: return null
    val label = when (unit) {
        RecurrenceUnit.D -> if (n == 1) "Daily" else "Every $n days"
        RecurrenceUnit.W -> if (n == 1) "Weekly" else "Every $n weeks"
        RecurrenceUnit.M -> if (n == 1) "Monthly" else "Every $n months"
        RecurrenceUnit.Y -> if (n == 1) "Yearly" else "Every $n years"
    }
    val approxDays = n * when (unit) {
        RecurrenceUnit.D -> 1
        RecurrenceUnit.W -> 7
        RecurrenceUnit.M -> 30
        RecurrenceUnit.Y -> 365
    }
    return when {
        approxDays <= 7 -> WebRecurrenceChip(label, Color(0xFFDDE7FF), Color(0xFF2F4FA8))
        approxDays <= 21 -> WebRecurrenceChip(label, Color(0xFFD8F0E4), Color(0xFF1F7A4D))
        else -> WebRecurrenceChip(label, Color(0xFFFBE3D3), Color(0xFF9A4A1F))
    }
}

/** The right pane for Screen.CategoryTasks — one subcategory's (or, with subcategoryId == null, the whole category's) task list: planned this week, then everything with a sort control. */
@Composable
private fun WebSubcategoryTasksPane(
    apiClient: ApiClient,
    sessionToken: String,
    category: TaskCategory,
    subcategoryId: Long?,
    allTasks: List<Task>,
    today: LocalDate,
    onBack: () -> Unit,
    onOpenTask: (Task) -> Unit,
    onRefresh: () -> Unit,
    onBulkAdd: () -> Unit,
    onCopied: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sort by remember(subcategoryId) { mutableStateOf(WebTaskSort.DEFAULT) }
    var showAllPlanned by remember(subcategoryId) { mutableStateOf(false) }

    val accent = category.displayColor.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val subcategory = subcategoryId?.let { id -> category.subcategories.find { it.id == id } }
    val subName = subcategory?.name

    var showCopyPrompt by remember(subcategoryId) { mutableStateOf(false) }
    var copyName by remember(subcategoryId) { mutableStateOf(subName?.let { "$it copy" } ?: "") }
    var copyBusy by remember(subcategoryId) { mutableStateOf(false) }
    var copyError by remember(subcategoryId) { mutableStateOf<String?>(null) }

    fun performCopy() {
        val sub = subcategory ?: return
        copyBusy = true
        copyError = null
        scope.launch {
            runCatching {
                val newSub = apiClient.createTaskSubcategory(
                    sessionToken,
                    category.id,
                    CreateTaskSubcategoryRequest(
                        name = copyName.ifBlank { "${sub.name} copy" },
                        priority = sub.priority,
                        color = sub.color,
                        ordered = sub.ordered,
                        linksToPerson = sub.linksToPerson,
                        linksToEvent = sub.linksToEvent,
                        recurrenceBase = sub.recurrenceBase,
                    ),
                )
                val sourceTasks = allTasks.filter {
                    it.taskCategoryId == category.id && it.subcategoryId == subcategoryId &&
                        it.status != TaskStatus.COMPLETED && it.status != TaskStatus.ABANDONED
                }.sortedWith(compareBy({ it.queuePosition ?: Int.MAX_VALUE }, { it.dueDate ?: "9999-99-99" }, { it.name.lowercase() }))
                var position = 0
                sourceTasks.forEach { task ->
                    apiClient.createTask(
                        sessionToken,
                        CreateTaskRequest(
                            name = task.name,
                            taskCategoryId = category.id,
                            subcategoryId = newSub.id,
                            dueDate = task.dueDate,
                            durationMinutes = task.durationMinutes,
                            recurrenceInterval = task.recurrenceInterval,
                            recurrenceUnit = task.recurrenceUnit,
                            recurrenceBase = task.recurrenceBase,
                            repeatsManually = task.repeatsManually,
                            queuePosition = if (task.queuePosition != null) position++ else null,
                        ),
                    )
                }
                newSub
            }.onSuccess { newSub ->
                copyBusy = false
                showCopyPrompt = false
                onRefresh()
                onCopied(newSub.id)
            }.onFailure {
                copyBusy = false
                copyError = it.serverMessage()
            }
        }
    }

    val weekStart = remember(today) { today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val weekEnd = remember(weekStart) { weekStart.plus(6, DateTimeUnit.DAY) }

    val tasks = allTasks.filter {
        it.taskCategoryId == category.id && it.status != TaskStatus.ABANDONED && (subcategoryId == null || it.subcategoryId == subcategoryId)
    }
    val planned = tasks.filter { it.status != TaskStatus.COMPLETED && it.dueDate != null && LocalDate.parse(it.dueDate) in weekStart..weekEnd }.sortedBy { it.dueDate }
    // Only meaningful for one subcategory's own queue (or a category with no subcategories at
    // all, where every task here shares one flat queue) — a "whole category" view mixing several
    // subcategories' tasks has no single comparable order, so don't attempt to number that one.
    val effectiveOrdered = when {
        subcategory != null -> subcategory.ordered ?: category.ordered
        category.subcategories.isEmpty() -> category.ordered
        else -> false
    }
    val sortedTasks = when (sort) {
        WebTaskSort.DEFAULT -> if (effectiveOrdered) tasks.sortedBy { it.queuePosition ?: Int.MAX_VALUE } else tasks
        WebTaskSort.DUE -> tasks.sortedBy { it.dueDate ?: "9999-99-99" }
        WebTaskSort.NAME -> tasks.sortedBy { it.name.lowercase() }
    }.sortedBy { it.status == TaskStatus.COMPLETED }
    val orderBadges = if (effectiveOrdered && sort == WebTaskSort.DEFAULT) {
        sortedTasks.filter { it.status != TaskStatus.COMPLETED }.mapIndexed { i, t -> t.id to (i + 1) }.toMap()
    } else emptyMap()
    val visiblePlanned = if (showAllPlanned) planned else planned.take(5)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onBack),
            )
            if (subName != null) {
                Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
        if (subName == null) {
            Text(category.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WebChip("+ Add tasks", selected = false, accent = accent, onClick = onBulkAdd)
            if (subcategory != null) {
                WebChip("⧉ Copy subcategory", selected = false, accent = accent) {
                    copyName = "${subcategory.name} copy"
                    copyError = null
                    showCopyPrompt = true
                }
            }
        }

        if (showCopyPrompt && subcategory != null) {
            WebCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Copy \"${subcategory.name}\" and its tasks to a new subcategory",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "Every open task here — same name, duration and repeat — is copied across too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    copyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = copyName,
                            onValueChange = { copyName = it },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = webFieldColors(accent),
                            modifier = Modifier.weight(1f),
                        )
                        WebChip("Cancel", selected = false, accent = accent) { showCopyPrompt = false }
                        AccentButton(
                            text = "Create copy",
                            onClick = ::performCopy,
                            accent = accent,
                            enabled = copyName.isNotBlank() && !copyBusy,
                            modifier = Modifier.height(44.dp),
                            content = if (copyBusy) {
                                { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) }
                            } else null,
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Planned this week · ${planned.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            if (planned.size > 5) {
                Text(
                    if (showAllPlanned) "Show less ›" else "View all ›",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { showAllPlanned = !showAllPlanned }.padding(vertical = 4.dp),
                )
            }
        }
        if (planned.isEmpty()) {
            Text("Nothing planned this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WebCard(modifier = Modifier.fillMaxWidth()) {
                visiblePlanned.forEachIndexed { index, task ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    WebTaskRow(
                        task = task,
                        accent = accent,
                        subtitle = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                WizardIconGlyph(WizardGlyph.CALENDAR, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(14.dp))
                                Text(dueLabel(task.dueDate, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onOpen = { onOpenTask(task) },
                        onComplete = { scope.launch { runCatching { apiClient.completeTask(sessionToken, task.id) }; onRefresh() } },
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("All tasks · ${tasks.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { sort = WebTaskSort.entries[(sort.ordinal + 1) % WebTaskSort.entries.size] }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WizardIconGlyph(WizardGlyph.ORDER, MaterialTheme.colorScheme.onSurface, Modifier.size(16.dp))
                Text("Sort: ${sort.label}", style = MaterialTheme.typography.bodyMedium)
                Text("›")
            }
        }

        if (tasks.isEmpty()) {
            Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WebCard(modifier = Modifier.fillMaxWidth()) {
                sortedTasks.forEachIndexed { index, task ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    val chip = webRecurrenceChip(task)
                    WebTaskRow(
                        task = task,
                        accent = accent,
                        orderBadge = orderBadges[task.id],
                        subtitle = {
                            if (chip != null) {
                                Text(
                                    chip.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = chip.content,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(chip.background).padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        },
                        onOpen = { onOpenTask(task) },
                        onComplete = { scope.launch { runCatching { apiClient.completeTask(sessionToken, task.id) }; onRefresh() } },
                    )
                }
            }
        }
    }
}

private fun RecurrenceUnit.pluralBulk(): String = when (this) {
    RecurrenceUnit.D -> "days"
    RecurrenceUnit.W -> "weeks"
    RecurrenceUnit.M -> "months"
    RecurrenceUnit.Y -> "years"
}

/** No Manual here — bulk-add is always an auto-scheduled repeat, just "every 1 <unit>" for the presets or a typed count for Custom. */
private enum class WebBulkFrequency(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom") }

private data class WebBulkRow(
    val id: Int,
    val name: String,
    val dueDate: String? = null,
    val durationMinutes: String = "30",
    val repeatable: Boolean = false,
    val frequency: WebBulkFrequency = WebBulkFrequency.WEEKLY,
    // Only read when frequency == CUSTOM — e.g. "3" + D = "every 3 days". The presets above set these to "1" + the matching unit.
    val recurrenceInterval: String = "1",
    val recurrenceUnit: RecurrenceUnit = RecurrenceUnit.D,
    val recurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val error: String? = null,
)

/**
 * The right pane for Screen.BulkAddTasks — several tasks at once for one
 * category/subcategory, added right here in the master-detail shell (the
 * sidebar stays put) rather than navigating away to its own page. Each row
 * is its own card with its own due date/duration/repeat settings; only
 * who it's assigned to and its linked event are shared across the batch.
 * A row is created immediately on submit and removed from the list on
 * success, so a partial failure just leaves the failed rows behind to retry.
 */
@Composable
private fun WebBulkAddTasksPane(
    apiClient: ApiClient,
    sessionToken: String,
    category: TaskCategory,
    subcategoryId: Long?,
    allTasks: List<Task>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var loadingPeople by remember { mutableStateOf(true) }

    var rows by remember(category.id, subcategoryId) { mutableStateOf(listOf(WebBulkRow(id = 0, name = ""))) }
    var nextId by remember(category.id, subcategoryId) { mutableStateOf(1) }
    var createdCount by remember(category.id, subcategoryId) { mutableStateOf(0) }

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var personMenuExpanded by remember { mutableStateOf(false) }
    var linkedEvent by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { apiClient.fetchPeople(sessionToken) }.onSuccess { people = it }
        loadingPeople = false
    }

    val subcategory = subcategoryId?.let { id -> category.subcategories.find { it.id == id } }
    val accent = category.displayColor.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    val fieldColors = webFieldColors(accent)

    val effectiveOrdered = subcategory?.ordered ?: category.ordered
    val effectiveLinksToPerson = subcategory?.linksToPerson ?: category.linksToPerson
    val effectiveLinksToEvent = subcategory?.linksToEvent ?: category.linksToEvent

    fun updateRow(id: Int, transform: (WebBulkRow) -> WebBulkRow) {
        rows = rows.map { if (it.id == id) transform(it).copy(error = null) else it }
    }

    fun chooseFrequency(id: Int, choice: WebBulkFrequency) {
        updateRow(id) { row ->
            when (choice) {
                WebBulkFrequency.DAILY -> row.copy(frequency = choice, recurrenceInterval = "1", recurrenceUnit = RecurrenceUnit.D)
                WebBulkFrequency.WEEKLY -> row.copy(frequency = choice, recurrenceInterval = "1", recurrenceUnit = RecurrenceUnit.W)
                WebBulkFrequency.MONTHLY -> row.copy(frequency = choice, recurrenceInterval = "1", recurrenceUnit = RecurrenceUnit.M)
                WebBulkFrequency.YEARLY -> row.copy(frequency = choice, recurrenceInterval = "1", recurrenceUnit = RecurrenceUnit.Y)
                WebBulkFrequency.CUSTOM -> row.copy(frequency = choice, recurrenceInterval = if ((row.recurrenceInterval.toIntOrNull() ?: 0) < 1) "3" else row.recurrenceInterval)
            }
        }
    }

    fun submit() {
        submitting = true
        scope.launch {
            var nextQueuePosition = if (effectiveOrdered) {
                (allTasks.filter {
                    it.taskCategoryId == category.id && it.subcategoryId == subcategoryId && it.status != TaskStatus.COMPLETED && it.queuePosition != null
                }.mapNotNull { it.queuePosition }.maxOrNull() ?: -1) + 1
            } else 0

            val toCreate = rows.filter { it.name.isNotBlank() }
            val updated = rows.toMutableList()
            for (row in toCreate) {
                val result = runCatching {
                    apiClient.createTask(
                        sessionToken,
                        CreateTaskRequest(
                            name = row.name,
                            taskCategoryId = category.id,
                            subcategoryId = subcategoryId,
                            personId = if (effectiveLinksToPerson) selectedPerson?.id else null,
                            linkedEvent = if (effectiveLinksToEvent) linkedEvent.ifBlank { null } else null,
                            dueDate = row.dueDate,
                            durationMinutes = row.durationMinutes.toIntOrNull() ?: 30,
                            recurrenceInterval = if (row.repeatable) row.recurrenceInterval.toIntOrNull() ?: 1 else null,
                            recurrenceUnit = if (row.repeatable) row.recurrenceUnit else null,
                            recurrenceBase = row.recurrenceBase,
                            repeatsManually = false,
                            queuePosition = if (effectiveOrdered) nextQueuePosition else null,
                        ),
                    )
                }
                val index = updated.indexOfFirst { it.id == row.id }
                if (result.isSuccess) {
                    if (effectiveOrdered) nextQueuePosition++
                    createdCount++
                    if (index >= 0) updated.removeAt(index)
                } else {
                    if (index >= 0) updated[index] = row.copy(error = result.exceptionOrNull()?.serverMessage())
                }
            }
            rows = updated
            submitting = false
            onRefresh()
            if (rows.none { it.name.isNotBlank() }) onBack()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onBack),
            )
            if (subcategory != null) {
                Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subcategory.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Add tasks", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))

        if (loadingPeople) CircularProgressIndicator(modifier = Modifier.size(20.dp))

        if (effectiveLinksToPerson || effectiveLinksToEvent) {
            WebCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WebSectionLabel("Applies to every task below")
                    if (effectiveLinksToPerson) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Assign to", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(110.dp))
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
                    if (effectiveLinksToEvent) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Linked event", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(110.dp))
                            OutlinedTextField(
                                value = linkedEvent,
                                onValueChange = { linkedEvent = it },
                                placeholder = { Text("e.g. Christmas (optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        rows.forEach { row ->
            WebCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { newName -> updateRow(row.id) { it.copy(name = newName) } },
                            placeholder = { Text("Task name") },
                            singleLine = true,
                            isError = row.error != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        DueDateField(
                            value = row.dueDate,
                            onValueChange = { newDate -> updateRow(row.id) { it.copy(dueDate = newDate) } },
                            label = "Due",
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.width(150.dp),
                        )
                        OutlinedTextField(
                            value = row.durationMinutes,
                            onValueChange = { v -> updateRow(row.id) { it.copy(durationMinutes = v.filter { c -> c.isDigit() }) } },
                            label = { Text("Min") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.width(90.dp),
                        )
                        if (rows.size > 1) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { rows = rows.filter { it.id != row.id } },
                                contentAlignment = Alignment.Center,
                            ) { WizardIconGlyph(WizardGlyph.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(13.dp)) }
                        }
                    }
                    row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    WebChip(if (row.repeatable) "Repeats ✓" else "Repeats?", row.repeatable, accent) {
                        updateRow(row.id) { it.copy(repeatable = !it.repeatable) }
                    }
                    if (row.repeatable) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WebBulkFrequency.entries.forEach { option -> WebChip(option.label, row.frequency == option, accent) { chooseFrequency(row.id, option) } }
                        }
                        if (row.frequency == WebBulkFrequency.CUSTOM) {
                            // Its own row — cramming this into the frequency chips above is what didn't fit.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Every", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = row.recurrenceInterval,
                                    onValueChange = { v -> updateRow(row.id) { it.copy(recurrenceInterval = v.filter { c -> c.isDigit() }) } },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.width(64.dp),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RecurrenceUnit.entries.forEach { u ->
                                        WebChip(u.pluralBulk().replaceFirstChar { c -> c.uppercase() }, row.recurrenceUnit == u, accent) { updateRow(row.id) { it.copy(recurrenceUnit = u) } }
                                    }
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RecurrenceBase.entries.forEach { base ->
                                WebChip(base.shortLabel(), row.recurrenceBase == base, accent) { updateRow(row.id) { it.copy(recurrenceBase = base) } }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { rows = rows + WebBulkRow(id = nextId, name = ""); nextId++ }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WizardIconGlyph(WizardGlyph.PLUS, MaterialTheme.colorScheme.onSurface, Modifier.size(16.dp))
            Text("Add another task", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }

        if (effectiveOrdered) {
            Text(
                "Added to the end of the queue, in the order listed above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (createdCount > 0) {
            Text(
                "$createdCount task${if (createdCount == 1) "" else "s"} created so far.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AccentButton(
            text = "Create ${rows.count { it.name.isNotBlank() }} task${if (rows.count { it.name.isNotBlank() } == 1) "" else "s"}",
            onClick = ::submit,
            accent = accent,
            enabled = rows.any { it.name.isNotBlank() } && !submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            content = if (submitting) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
            } else null,
        )
    }
}

@Composable
private fun WebTaskRow(task: Task, accent: Color, subtitle: @Composable () -> Unit, onOpen: () -> Unit, onComplete: () -> Unit, orderBadge: Int? = null) {
    val done = task.status == TaskStatus.COMPLETED
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (orderBadge != null) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(orderBadge.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent)
            }
        }
        if (done) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            CompleteCheckbox(onClick = onComplete)
        }
        Column(modifier = Modifier.weight(1f).graphicsLayer(alpha = if (done) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            subtitle()
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}
