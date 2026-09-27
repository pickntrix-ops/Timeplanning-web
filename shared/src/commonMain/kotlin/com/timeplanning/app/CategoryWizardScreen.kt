package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
private fun wizardTitleStyle() = MaterialTheme.typography.headlineSmall.copy(
    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
)

private data class SubcategoryDraft(
    val localId: Int,
    var name: String,
    var color: String,
    var ordered: Boolean? = null,
    var linksToPerson: Boolean? = null,
    var linksToEvent: Boolean? = null,
    var recurrenceBase: RecurrenceBase? = null,
)

private fun SubcategoryDraft.subtitle(): String {
    val boolOverrides = listOfNotNull(ordered, linksToPerson, linksToEvent).size
    val total = boolOverrides + if (recurrenceBase != null) 1 else 0
    return when {
        total == 0 -> "Inherits category settings"
        total == 1 && recurrenceBase != null -> "Repeats ${if (recurrenceBase == RecurrenceBase.DUE_DATE) "from due date" else "from completion date"}"
        total == 1 -> "1 setting overridden"
        else -> "$total settings overridden"
    }
}

/**
 * A 4-step wizard for creating a category (Basics → Default behaviour →
 * Subcategories → Review), replacing the single-scroll create form per the
 * person's own reference screenshots. Editing an existing category still uses
 * CategoryFormScreen — only creation is a wizard, matching what was asked for.
 */
@Composable
fun CategoryWizardScreen(
    apiClient: ApiClient,
    sessionToken: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }

    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(CategoryIcon.default) }
    var color by remember { mutableStateOf(CategoryPalette.first()) }

    var ordered by remember { mutableStateOf(false) }
    var linksToPerson by remember { mutableStateOf(false) }
    var linksToEvent by remember { mutableStateOf(false) }
    var recurrenceBase by remember { mutableStateOf(RecurrenceBase.DUE_DATE) }
    var energyLevel by remember { mutableStateOf<EnergyLevel?>(null) }
    var suggestingEnergy by remember { mutableStateOf(false) }
    var autoSchedule by remember { mutableStateOf(true) }
    var weeklyHourCap by remember { mutableStateOf("") }

    var subcategories by remember { mutableStateOf(listOf<SubcategoryDraft>()) }
    var showSubForm by remember { mutableStateOf(false) }
    var editingDraftId by remember { mutableStateOf<Int?>(null) }
    var nextLocalId by remember { mutableStateOf(1) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Content preview only — the icon tile's selected wash, the colour-swatch ring, and the
    // live-preview tile intentionally show whatever colour the person actually picked.
    val accent = color.toColorOrNull() ?: MaterialTheme.colorScheme.primary

    // Everything else (buttons, the progress indicator, the text field's focus border) is fixed
    // UI chrome, not tied to the category's own colour — it shouldn't change as someone picks
    // a swatch, and it shouldn't inherit the app's teal theme either.
    val wizardPrimary = Color(0xFF4B3F72)

    // The reference design is a neutral cool-white, not the app's teal-tinted theme —
    // scoped to just this screen rather than changing the app-wide AppColorScheme.
    val wizardColorScheme = MaterialTheme.colorScheme.copy(
        primary = wizardPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3E0EC),
        onPrimaryContainer = wizardPrimary,
        background = Color(0xFFF8F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEEF0F3),
        onSurface = Color(0xFF15151C),
        onSurfaceVariant = Color(0xFF6C6C78),
        outline = Color(0xFFD7D7DD),
        outlineVariant = Color(0xFFE5E5EA),
    )

    fun suggestEnergy() {
        if (name.isBlank()) return
        suggestingEnergy = true
        error = null
        scope.launch {
            runCatching { apiClient.suggestCategoryEnergyLevel(sessionToken, name, null) }
                .onSuccess { suggestion -> if (suggestion != null) energyLevel = suggestion else error = "Couldn't come up with a suggestion — pick one yourself." }
                .onFailure { error = it.serverMessage() }
            suggestingEnergy = false
        }
    }

    fun submit() {
        loading = true
        error = null
        scope.launch {
            runCatching {
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
                        energyLevel = energyLevel,
                        weeklyHourCapMinutes = weeklyHourCap.hoursTextToMinutes(),
                        autoSchedule = autoSchedule,
                    ),
                )
                subcategories.forEach { d ->
                    apiClient.createTaskSubcategory(
                        sessionToken,
                        created.id,
                        CreateTaskSubcategoryRequest(
                            name = d.name,
                            color = d.color,
                            ordered = d.ordered,
                            linksToPerson = d.linksToPerson,
                            linksToEvent = d.linksToEvent,
                            recurrenceBase = d.recurrenceBase,
                        ),
                    )
                }
            }.onSuccess { onDone() }
                .onFailure { error = it.serverMessage() }
            loading = false
        }
    }

    MaterialTheme(colorScheme = wizardColorScheme) {
    Column(modifier = Modifier.fillMaxSize().background(wizardColorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevronButton(onClick = { if (step == 1) onCancel() else step-- })
            Row(verticalAlignment = Alignment.CenterVertically) {
                val inactive = Color(0xFFB8B8C2)
                repeat(4) { i ->
                    val reached = i < step
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (reached) wizardPrimary else inactive),
                    )
                    if (i < 3) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(2.dp)
                                .background(if (i + 1 < step) wizardPrimary else inactive),
                        )
                    }
                }
            }
            Text("$step of 4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (step) {
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Create a category", style = wizardTitleStyle())
                        Text(
                            "Let's start with the basics. You can customise how tasks behave next.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FormSection("Category name") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("e.g. Home, Work, Personal") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FormSection("Choose an icon") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CategoryIcon.entries.chunked(4).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { ic ->
                                        IconTile(
                                            ic,
                                            selected = icon == ic,
                                            onClick = { icon = ic },
                                            accent = accent,
                                            modifier = Modifier.weight(1f).height(60.dp),
                                        )
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    FormSection("Choose a colour") {
                        ColorSwatchPicker(selected = color, onSelect = { color = it })
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Live preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) { CategoryIconGlyph(icon, accent, Modifier.size(22.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name.ifBlank { "New category" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "0 tasks · No subcategories",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Default behaviour", style = wizardTitleStyle())
                        Text(
                            "Choose how tasks in this category will behave by default. You can always change these later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleRow(
                            title = "Tasks are done in order",
                            description = "Tasks in this category should be completed sequentially.",
                            checked = ordered,
                            onCheckedChange = { ordered = it },
                            accent = wizardPrimary,
                            icon = { m -> WizardIconGlyph(WizardGlyph.ORDER, MaterialTheme.colorScheme.onSurfaceVariant, m) },
                        )
                        ToggleRow(
                            title = "Tasks can link to a person",
                            description = "You can link tasks in this category to a person.",
                            checked = linksToPerson,
                            onCheckedChange = { linksToPerson = it },
                            accent = wizardPrimary,
                            icon = { m -> CategoryIconGlyph(CategoryIcon.PEOPLE, MaterialTheme.colorScheme.onSurfaceVariant, m) },
                        )
                        ToggleRow(
                            title = "Tasks can link to a calendar event",
                            description = "You can link tasks in this category to a calendar event.",
                            checked = linksToEvent,
                            onCheckedChange = { linksToEvent = it },
                            accent = wizardPrimary,
                            icon = { m -> WizardIconGlyph(WizardGlyph.CALENDAR, MaterialTheme.colorScheme.onSurfaceVariant, m) },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Repeating tasks", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Choose how repeating tasks in this category should behave by default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RecurrenceOptionCard(
                                title = "Fixed from due date",
                                description = "The next due date is based on the original due date. Useful for things like bills or birthdays.",
                                selected = recurrenceBase == RecurrenceBase.DUE_DATE,
                                onClick = { recurrenceBase = RecurrenceBase.DUE_DATE },
                                accent = wizardPrimary,
                                modifier = Modifier.weight(1f),
                                icon = { m -> WizardIconGlyph(WizardGlyph.CALENDAR, if (recurrenceBase == RecurrenceBase.DUE_DATE) wizardPrimary else MaterialTheme.colorScheme.onSurfaceVariant, m) },
                            )
                            RecurrenceOptionCard(
                                title = "From completion date",
                                description = "The next due date is based on when the task is completed. Useful for things like cleaning or regular chores.",
                                selected = recurrenceBase == RecurrenceBase.COMPLETION_DATE,
                                onClick = { recurrenceBase = RecurrenceBase.COMPLETION_DATE },
                                accent = wizardPrimary,
                                modifier = Modifier.weight(1f),
                                icon = { m -> WizardIconGlyph(WizardGlyph.CHECK_CIRCLE, if (recurrenceBase == RecurrenceBase.COMPLETION_DATE) wizardPrimary else MaterialTheme.colorScheme.onSurfaceVariant, m) },
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Energy level", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = ::suggestEnergy, enabled = name.isNotBlank() && !suggestingEnergy) {
                                if (suggestingEnergy) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = wizardPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text("✨ Suggest", color = wizardPrimary)
                                }
                            }
                        }
                        Text(
                            "Not required — used later to plan when in the day this category's tasks fit best.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectableChip("Any", energyLevel == null) { energyLevel = null }
                            EnergyLevel.entries.forEach { level -> SelectableChip(level.label(), energyLevel == level) { energyLevel = level } }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Plan this into my week automatically", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (autoSchedule) "Tasks here can be auto-scheduled, up to any cap below." else "You'll add tasks/hours from this category yourself. Daily tasks and tasks with a due date are still always scheduled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = autoSchedule, onCheckedChange = { autoSchedule = it }, colors = SwitchDefaults.colors(checkedTrackColor = wizardPrimary))
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
                }

                3 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Subcategories", style = wizardTitleStyle())
                        Text(
                            "Add subcategories to organise your tasks even further. Each subcategory can have its own settings, or inherit the category defaults.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (subcategories.isEmpty() && !showSubForm) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(6.dp))
                            Text("No subcategories yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add subcategories to keep your tasks more organised.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            AccentButton(
                                text = "+ Add your first subcategory",
                                onClick = { editingDraftId = null; showSubForm = true },
                                accent = wizardPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(
                                onClick = { step = 4 },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, wizardPrimary),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = wizardPrimary),
                            ) {
                                Text("Skip for now", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        if (subcategories.isNotEmpty()) {
                            var quickName by remember { mutableStateOf("") }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = quickName,
                                    onValueChange = { quickName = it },
                                    placeholder = { Text("e.g. Cleaning, Bills, Maintenance") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(wizardPrimary)
                                        .clickable {
                                            editingDraftId = null
                                            showSubForm = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text("+", style = MaterialTheme.typography.titleLarge, color = Color.White) }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                subcategories.forEach { draft ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                editingDraftId = draft.localId
                                                showSubForm = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(draft.color.toColorOrNull() ?: Color.Gray))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(draft.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(draft.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            if (!showSubForm) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(wizardPrimary.copy(alpha = 0.08f))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("💡", style = MaterialTheme.typography.bodyLarge)
                                    Column {
                                        Text("Each subcategory can override settings", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "By default, subcategories inherit the category settings. Tap a subcategory to change its settings.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        if (showSubForm) {
                            val editing = subcategories.find { it.localId == editingDraftId }
                            SubcategoryDraftForm(
                                initial = editing,
                                nextColor = { CategoryPalette[subcategories.size % CategoryPalette.size] },
                                accent = wizardPrimary,
                                categoryOrdered = ordered,
                                categoryLinksToPerson = linksToPerson,
                                categoryLinksToEvent = linksToEvent,
                                categoryRecurrenceBase = recurrenceBase,
                                onCancel = { showSubForm = false; editingDraftId = null },
                                onRemove = if (editing != null) {
                                    {
                                        subcategories = subcategories.filterNot { it.localId == editing.localId }
                                        showSubForm = false
                                        editingDraftId = null
                                    }
                                } else null,
                                onSave = { draft ->
                                    subcategories = if (editing != null) {
                                        subcategories.map { if (it.localId == editing.localId) draft else it }
                                    } else {
                                        subcategories + draft.copy(localId = nextLocalId++)
                                    }
                                    showSubForm = false
                                    editingDraftId = null
                                },
                            )
                        } else if (subcategories.isNotEmpty()) {
                            AccentButton(
                                text = "Continue",
                                onClick = { step = 4 },
                                accent = wizardPrimary,
                                showArrow = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                4 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Review & create", style = wizardTitleStyle())
                        Text(
                            "Check your settings below. You can always change these later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    ReviewRow(
                        onClick = { step = 1 },
                        leading = {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) { CategoryIconGlyph(icon, accent, Modifier.size(18.dp)) }
                        },
                        title = "Category name",
                        subtitle = name.ifBlank { "Untitled" },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Default behaviour", style = MaterialTheme.typography.titleSmall)
                        ReadOnlyToggleRow("Tasks are done in order", ordered, wizardPrimary) { m -> WizardIconGlyph(WizardGlyph.ORDER, MaterialTheme.colorScheme.onSurfaceVariant, m) }
                        ReadOnlyToggleRow("Can link to a person", linksToPerson, wizardPrimary) { m -> CategoryIconGlyph(CategoryIcon.PEOPLE, MaterialTheme.colorScheme.onSurfaceVariant, m) }
                        ReadOnlyToggleRow("Can link to a calendar event", linksToEvent, wizardPrimary) { m -> WizardIconGlyph(WizardGlyph.CALENDAR, MaterialTheme.colorScheme.onSurfaceVariant, m) }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { step = 2 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Repeating tasks", style = MaterialTheme.typography.bodyLarge)
                                Text(recurrenceBase.shortLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { step = 2 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Energy level", style = MaterialTheme.typography.bodyLarge)
                                Text(energyLevel?.label() ?: "Any", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Subcategories (${subcategories.size})", style = MaterialTheme.typography.titleSmall)
                        if (subcategories.isEmpty()) {
                            Text("No subcategories", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        subcategories.forEach { draft ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { editingDraftId = draft.localId; showSubForm = true; step = 3 },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(draft.color.toColorOrNull() ?: Color.Gray))
                                    Text(draft.name, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (draft.subtitle() == "Inherits category settings") "Inherits" else "Custom",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        TextButton(onClick = { editingDraftId = null; showSubForm = true; step = 3 }) {
                            Text("+ Add another subcategory", color = wizardPrimary)
                        }
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    AccentButton(
                        text = "Create category",
                        onClick = ::submit,
                        accent = wizardPrimary,
                        enabled = name.isNotBlank() && !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (loading) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Text("Creating category...", style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            Text("Create category", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            Box(modifier = Modifier.navigationBarsPadding().height(4.dp))
        }

        if (step in 1..2) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                AccentButton(
                    text = "Continue",
                    onClick = { step++ },
                    accent = wizardPrimary,
                    showArrow = true,
                    enabled = step > 1 || name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun ReadOnlyToggleRow(title: String, checked: Boolean, accent: Color, icon: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = false, colors = SwitchDefaults.colors(disabledCheckedTrackColor = accent.copy(alpha = 0.5f)))
    }
}

@Composable
private fun ReviewRow(onClick: () -> Unit, leading: @Composable () -> Unit, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.titleMedium)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubcategoryDraftForm(
    initial: SubcategoryDraft?,
    nextColor: () -> String,
    accent: Color,
    categoryOrdered: Boolean,
    categoryLinksToPerson: Boolean,
    categoryLinksToEvent: Boolean,
    categoryRecurrenceBase: RecurrenceBase,
    onCancel: () -> Unit,
    onRemove: (() -> Unit)?,
    onSave: (SubcategoryDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: nextColor()) }
    var useCategorySettings by remember { mutableStateOf(initial == null || (initial.ordered == null && initial.linksToPerson == null && initial.linksToEvent == null && initial.recurrenceBase == null)) }
    var ordered by remember { mutableStateOf(initial?.ordered ?: categoryOrdered) }
    var linksToPerson by remember { mutableStateOf(initial?.linksToPerson ?: categoryLinksToPerson) }
    var linksToEvent by remember { mutableStateOf(initial?.linksToEvent ?: categoryLinksToEvent) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var recurrenceOverride by remember { mutableStateOf(initial?.recurrenceBase) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (initial != null) "Edit subcategory" else "New subcategory", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Subcategory name") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.toColorOrNull() ?: Color.Gray)
                    .clickable {
                        val idx = CategoryPalette.indexOf(color)
                        color = CategoryPalette[(idx + 1).mod(CategoryPalette.size)]
                    },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Use category settings", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Inherit all settings from the category, or customise just for this subcategory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = useCategorySettings, onCheckedChange = { useCategorySettings = it })
        }

        if (!useCategorySettings) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Override settings (optional)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Only change the settings you want to be different to the category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MiniToggleRow("Tasks are done in order", ordered) { ordered = it }
            MiniToggleRow("Can link to a person", linksToPerson) { linksToPerson = it }
            MiniToggleRow("Can link to a calendar event", linksToEvent) { linksToEvent = it }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { recurrenceExpanded = !recurrenceExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Repeating tasks", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            recurrenceOverride?.shortLabel() ?: "Inherit category setting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (recurrenceExpanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableChip("Inherit (${categoryRecurrenceBase.shortLabel()})", recurrenceOverride == null) { recurrenceOverride = null }
                        RecurrenceBase.entries.forEach { base ->
                            SelectableChip(base.shortLabel(), recurrenceOverride == base) { recurrenceOverride = base }
                        }
                    }
                }
            }
        }

        AccentButton(
            text = if (initial != null) "Save subcategory" else "Add subcategory",
            enabled = name.isNotBlank(),
            accent = accent,
            onClick = {
                onSave(
                    SubcategoryDraft(
                        localId = initial?.localId ?: 0,
                        name = name,
                        color = color,
                        ordered = if (useCategorySettings) null else ordered,
                        linksToPerson = if (useCategorySettings) null else linksToPerson,
                        linksToEvent = if (useCategorySettings) null else linksToEvent,
                        recurrenceBase = if (useCategorySettings) null else recurrenceOverride,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun MiniToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (checked) "On" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
