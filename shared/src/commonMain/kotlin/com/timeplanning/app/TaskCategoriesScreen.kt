package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * User-defined replacement for the old fixed TaskType — a category (e.g.
 * "Cleaning", "Job", "Etsy Orders") with its own default settings, and
 * private subcategories that can each override those defaults (e.g.
 * "Project" defaults ordered=true, its "Etsy Prints" subcategory overrides
 * that back to false). This is just the flat list — tapping a category
 * opens CategoryDetailScreen for its subcategories/linked tasks, and
 * creating/editing a category is its own full screen (CategoryFormScreen).
 */
@Composable
fun TaskCategoriesScreen(
    apiClient: ApiClient,
    sessionToken: String,
    currentTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    onOpenCategory: (TaskCategory) -> Unit,
    onAddCategory: () -> Unit,
    onAddTask: () -> Unit,
    refreshKey: Int,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }

    LaunchedEffect(refreshKey) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BottomNavBar(current = currentTab, onSelect = onSelectTab) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Tasks", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Your routines, jobs and life admin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF4B3F72)).clickable(onClick = onAddTask),
                    contentAlignment = Alignment.Center,
                ) { WizardIconGlyph(WizardGlyph.PLUS, Color.White, Modifier.size(22.dp)) }
            }

            if (loading && categories.isEmpty()) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp),
            ) {
                items(categories) { category ->
                    val inWeek = tasks.filter {
                        it.taskCategoryId == category.id && it.status != TaskStatus.ABANDONED &&
                            it.dueDate != null && LocalDate.parse(it.dueDate) in weekStart..weekEnd
                    }
                    CategoryCard(
                        category = category,
                        completed = inWeek.count { it.status == TaskStatus.COMPLETED },
                        total = inWeek.size,
                        onClick = { onOpenCategory(category) },
                    )
                }

                if (!loading && categories.isEmpty()) {
                    item {
                        Text(
                            "No categories yet — create your first one, e.g. Cleaning, Life Admin or Projects.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onAddCategory)
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                        Text("New category", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: TaskCategory, completed: Int, total: Int, onClick: () -> Unit) {
    val accent = category.displayColor.toColorOrNull() ?: Color.Gray
    val percent = if (total == 0) 0f else completed.toFloat() / total
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { CategoryIconGlyph(category.displayIcon, accent, Modifier.size(30.dp)) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(category.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text("$completed/$total this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(percent.coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(accent),
                    )
                }
                Text("${(percent * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A small round "+" button for a screen's top-right corner — used where a FloatingActionButton would otherwise be ambiguous with the app-wide "add task" FAB. */
@Composable
internal fun AddIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryPalette.forEach { hex ->
            val color = hex.toColorOrNull() ?: Color.Gray
            val isSelected = hex == selected
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.border(2.dp, color, CircleShape)
                        else Modifier,
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(if (isSelected) 26.dp else 34.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/** A 3-way inherit/on/off control — null means "inherit the category's default", shown alongside for context. */
@Composable
internal fun TriStateRow(label: String, value: Boolean?, categoryDefault: Boolean, onChange: (Boolean?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip("Inherit (${if (categoryDefault) "on" else "off"})", value == null) { onChange(null) }
            SelectableChip("On", value == true) { onChange(true) }
            SelectableChip("Off", value == false) { onChange(false) }
        }
    }
}
