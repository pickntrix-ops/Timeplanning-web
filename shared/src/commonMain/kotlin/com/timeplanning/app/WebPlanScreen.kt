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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Desktop-web version of PlanScreen — same underlying concept and mostly
 * the same reused pieces (EnergyRangeSlider, DayPreviewTimeline, autosave
 * logic), but genuinely wide desktop UI: a two-column card layout inside
 * the persistent WebTopNav shell, not the mobile screen narrowed into a
 * phone-shaped column. See PlanScreen.kt's own doc comment for what this
 * feature actually is (input for a not-yet-built scheduling algorithm).
 */
@Composable
fun WebPlanScreen(
    apiClient: ApiClient,
    sessionToken: String,
    currentTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var profiles by remember { mutableStateOf(mapOf<DayType, DayProfile>()) }
    var selectedDayType by remember { mutableStateOf(DayType.WEEKDAY) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var savedJustNow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var changeTick by remember { mutableStateOf(0) }

    var wakeTime by remember { mutableStateOf("") }
    var eveningCutoff by remember { mutableStateOf("") }
    var highStart by remember { mutableStateOf("") }
    var highEnd by remember { mutableStateOf("") }
    var mediumStart by remember { mutableStateOf("") }
    var mediumEnd by remember { mutableStateOf("") }
    var lowStart by remember { mutableStateOf("") }
    var lowEnd by remember { mutableStateOf("") }
    var commitments by remember { mutableStateOf(listOf<FixedCommitmentDraft>()) }
    var nextCommitmentId by remember { mutableStateOf(0) }

    var freeDays by remember { mutableStateOf(setOf<Weekday>()) }
    var savingFreeDays by remember { mutableStateOf(false) }
    var freeTimePercent by remember { mutableStateOf("30") }
    var freeTimePercentTick by remember { mutableStateOf(0) }

    fun loadFieldsFrom(profile: DayProfile?) {
        wakeTime = profile?.wakeTime?.toShortTimeOrSelf() ?: ""
        eveningCutoff = profile?.eveningCutoff?.toShortTimeOrSelf() ?: ""
        highStart = profile?.highEnergyStart?.toShortTimeOrSelf() ?: ""
        highEnd = profile?.highEnergyEnd?.toShortTimeOrSelf() ?: ""
        mediumStart = profile?.mediumEnergyStart?.toShortTimeOrSelf() ?: ""
        mediumEnd = profile?.mediumEnergyEnd?.toShortTimeOrSelf() ?: ""
        lowStart = profile?.lowEnergyStart?.toShortTimeOrSelf() ?: ""
        lowEnd = profile?.lowEnergyEnd?.toShortTimeOrSelf() ?: ""
        commitments = profile?.fixedCommitments.orEmpty().mapIndexed { i, c ->
            FixedCommitmentDraft(i, c.label, c.startTime.toShortTimeOrSelf(), c.endTime.toShortTimeOrSelf(), c.categoryId, c.subcategoryId)
        }
        nextCommitmentId = commitments.size
    }

    fun toggleFreeDay(day: Weekday) {
        val updated = if (day in freeDays) freeDays - day else freeDays + day
        val previous = freeDays
        freeDays = updated
        savingFreeDays = true
        scope.launch {
            runCatching { apiClient.updateFreeDays(sessionToken, updated) }
                .onSuccess { freeDays = it }
                .onFailure { freeDays = previous; error = "Couldn't save: ${it.serverMessage()}" }
            savingFreeDays = false
        }
    }

    fun selectDayType(dayType: DayType) {
        selectedDayType = dayType
        loadFieldsFrom(profiles[dayType])
    }

    // See PlanScreen.kt's setLevelTimes — writes a segment's time range back to whichever named window
    // (high/medium/low) that level owns, so boundary drags and label cycling both stay consistent.
    fun setLevelTimes(level: EnergyLevel, startMinutes: Int, endMinutes: Int) {
        val start = minutesToTime(startMinutes)
        val end = minutesToTime(endMinutes)
        when (level) {
            EnergyLevel.HIGH -> { highStart = start; highEnd = end }
            EnergyLevel.MEDIUM -> { mediumStart = start; mediumEnd = end }
            EnergyLevel.LOW -> { lowStart = start; lowEnd = end }
        }
    }

    fun save() {
        saving = true
        error = null
        scope.launch {
            runCatching {
                apiClient.updateDayProfile(
                    sessionToken,
                    selectedDayType,
                    UpdateDayProfileRequest(
                        wakeTime = wakeTime.ifBlank { null },
                        eveningCutoff = eveningCutoff.ifBlank { null },
                        highEnergyStart = highStart.ifBlank { null },
                        highEnergyEnd = highEnd.ifBlank { null },
                        mediumEnergyStart = mediumStart.ifBlank { null },
                        mediumEnergyEnd = mediumEnd.ifBlank { null },
                        lowEnergyStart = lowStart.ifBlank { null },
                        lowEnergyEnd = lowEnd.ifBlank { null },
                        fixedCommitments = commitments
                            .filter { it.label.isNotBlank() && it.startTime.isNotBlank() && it.endTime.isNotBlank() }
                            .map { FixedCommitment(it.label, it.startTime, it.endTime, it.categoryId, it.subcategoryId) },
                    ),
                )
            }.onSuccess { saved ->
                profiles = profiles + (selectedDayType to saved)
                savedJustNow = true
            }.onFailure { error = "Couldn't save: ${it.serverMessage()}" }
            saving = false
        }
    }

    fun markDirty() { changeTick++ }

    LaunchedEffect(changeTick) {
        if (changeTick == 0) return@LaunchedEffect
        delay(700)
        save()
    }

    LaunchedEffect(savedJustNow) {
        if (savedJustNow) {
            delay(1800)
            savedJustNow = false
        }
    }

    // Debounced independently of changeTick/save() — freeTimePercent lives on the user, not the DayProfile.
    LaunchedEffect(freeTimePercentTick) {
        if (freeTimePercentTick == 0) return@LaunchedEffect
        delay(700)
        val pct = freeTimePercent.toIntOrNull() ?: return@LaunchedEffect
        runCatching { apiClient.updatePlanPreferences(sessionToken, pct) }
            .onFailure { error = "Couldn't save: ${it.serverMessage()}" }
    }

    LaunchedEffect(Unit) {
        runCatching { apiClient.fetchTaskCategories(sessionToken) }.onSuccess { categories = it }
        runCatching { apiClient.fetchFreeDays(sessionToken) }.onSuccess { freeDays = it }
        runCatching { apiClient.fetchPlanPreferences(sessionToken) }.onSuccess { freeTimePercent = it.toString() }
        runCatching { apiClient.fetchDayProfiles(sessionToken) }
            .onSuccess { list ->
                profiles = list.associateBy { it.dayType }
                loadFieldsFrom(profiles[selectedDayType])
            }
            .onFailure { error = "Couldn't load your routine: ${it.serverMessage()}" }
        loading = false
    }

    val wakeMinutes = timeToMinutes(wakeTime)
    val cutoffMinutes = timeToMinutes(eveningCutoff)
    val energyBoundsValid = wakeMinutes != null && cutoffMinutes != null && cutoffMinutes > wakeMinutes
    val segmentLevels = deriveSegmentLevels(highStart, mediumStart, lowStart)
    val boundary1 = if (energyBoundsValid) {
        timeToMinutes(segmentLevels[0].endTimeOf(highEnd, mediumEnd, lowEnd))?.coerceIn(wakeMinutes!!, cutoffMinutes!!)
            ?: roundTo10(wakeMinutes + (cutoffMinutes - wakeMinutes) / 3)
    } else 0
    val boundary2 = if (energyBoundsValid) {
        timeToMinutes(segmentLevels[1].endTimeOf(highEnd, mediumEnd, lowEnd))?.coerceIn(wakeMinutes!!, cutoffMinutes!!)
            ?: roundTo10(wakeMinutes + (cutoffMinutes - wakeMinutes) * 2 / 3)
    } else 0
    val fieldColors = webFieldColors(PlanHeaderGreen)

    Column(modifier = Modifier.fillMaxSize().background(WebColorScheme.background)) {
        WebTopNav(currentTab, onSelectTab)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PlanHeaderGreen).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Plan", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        if (saving || savedJustNow) {
                            Text(if (saving) "Saving…" else "Saved", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Text(
                        "Set your routine and availability so tasks can be planned around your life.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }

                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                // No tabs — Settings and Preview are both always visible, side by side, since there's
                // plenty of width on desktop (unlike the phone-width mobile PlanScreen, which still tabs).
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            WebPlanCard(
                                icon = WizardGlyph.CHECK_CIRCLE,
                                iconTint = EnergyHighColor,
                                title = "Free days",
                                description = "Choose any days you want to keep free from discretionary planning.",
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Weekday.entries.forEach { day ->
                                        val selected = day in freeDays
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (selected) PlanHeaderGreen else Color.White)
                                                .border(1.dp, if (selected) PlanHeaderGreen else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                                .clickable(enabled = !savingFreeDays) { toggleFreeDay(day) }
                                                .padding(vertical = 11.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (selected) WizardIconGlyph(WizardGlyph.CHECK, Color.White, Modifier.size(11.dp))
                                            Text(
                                                day.shortLabel(),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }

                            WebPlanCard(
                                icon = WizardGlyph.INFO,
                                iconTint = PlanHeaderGreen,
                                title = "Free time",
                                description = "Roughly how much of each day's free time should stay unscheduled, so your plan doesn't fill up completely. Daily tasks and tasks with a due date are still always scheduled.",
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = freeTimePercent,
                                        onValueChange = { input ->
                                            freeTimePercent = input.filter { c -> c.isDigit() }.take(3)
                                            freeTimePercentTick++
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.width(120.dp),
                                    )
                                    Text("%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            WebPlanCard(
                                icon = WizardGlyph.CLOCK,
                                iconTint = PlanHeaderGreen,
                                title = "Your routine",
                                description = "Set up a typical weekday and weekend day. These are used to work out when things fit.",
                            ) {
                                DayTypeSegmentedControl(selectedDayType) { selectDayType(it) }
                            }

                            WebPlanCard(
                                icon = WizardGlyph.SUN,
                                iconTint = Color(0xFFE08A2B),
                                title = "Day bounds",
                                description = "Set when your day starts and when flexible tasks should stop.",
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = wakeTime,
                                        onValueChange = { wakeTime = it; markDirty() },
                                        label = { Text("Wake time") },
                                        placeholder = { Text("HH:mm") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = eveningCutoff,
                                        onValueChange = { eveningCutoff = it; markDirty() },
                                        label = { Text("Evening cutoff") },
                                        placeholder = { Text("HH:mm") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            WebPlanCard(
                                icon = WizardGlyph.BOLT,
                                iconTint = Color(0xFFE08A2B),
                                title = "Energy windows",
                                description = "Drag the handles to set when you typically have each energy level during the day.",
                            ) {
                                if (!energyBoundsValid) {
                                    Text(
                                        "Set your wake time and evening cutoff first.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    // Writes all three segments' times from a (possibly reordered) level assignment
                                    // — a cycle swaps two positions at once, so both must be re-written to stay contiguous.
                                    fun applySegments(updated: List<EnergyLevel>) {
                                        setLevelTimes(updated[0], wakeMinutes!!, boundary1)
                                        setLevelTimes(updated[1], boundary1, boundary2)
                                        setLevelTimes(updated[2], boundary2, cutoffMinutes!!)
                                        markDirty()
                                    }

                                    EnergyRangeSlider(
                                        startMinutes = wakeMinutes!!,
                                        endMinutes = cutoffMinutes!!,
                                        boundary1 = boundary1,
                                        boundary2 = boundary2,
                                        segmentLevels = segmentLevels,
                                        onBoundariesChange = { b1, b2 ->
                                            setLevelTimes(segmentLevels[0], wakeMinutes!!, b1)
                                            setLevelTimes(segmentLevels[1], b1, b2)
                                            setLevelTimes(segmentLevels[2], b2, cutoffMinutes!!)
                                        },
                                        onDragFinished = { markDirty() },
                                    )
                                    Text(
                                        "Click a label to change what energy level it represents.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        EnergySummaryChip(segmentLevels[0], wakeMinutes!!, boundary1, onClick = { applySegments(cycleSegmentLevels(segmentLevels, 0)) }, modifier = Modifier.weight(1f))
                                        EnergySummaryChip(segmentLevels[1], boundary1, boundary2, onClick = { applySegments(cycleSegmentLevels(segmentLevels, 1)) }, modifier = Modifier.weight(1f))
                                        EnergySummaryChip(segmentLevels[2], boundary2, cutoffMinutes!!, onClick = { applySegments(cycleSegmentLevels(segmentLevels, 2)) }, modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            WebPlanCard(
                                icon = WizardGlyph.CALENDAR,
                                iconTint = EnergyHighColor,
                                title = "Fixed commitments",
                                description = "Add regular commitments that aren't in your calendar, like work, meals or classes.",
                            ) {
                                commitments.forEach { commitment ->
                                    WebFixedCommitmentRow(
                                        commitment = commitment,
                                        categories = categories,
                                        fieldColors = fieldColors,
                                        onChange = { updated -> commitments = commitments.map { if (it.id == commitment.id) updated else it }; markDirty() },
                                        onRemove = { commitments = commitments.filter { it.id != commitment.id }; markDirty() },
                                    )
                                }
                                WebChip("+ Add a fixed commitment", selected = false, accent = PlanHeaderGreen) {
                                    commitments = commitments + FixedCommitmentDraft(nextCommitmentId, "", "", "")
                                    nextCommitmentId++
                                }
                            }
                        }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("Preview", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        WebPlanCard(
                            icon = WizardGlyph.CALENDAR,
                            iconTint = EnergyHighColor,
                            title = "Preview",
                            description = "This shows how your settings create available time during a typical ${selectedDayType.label().lowercase()}. Your actual tasks will appear here once auto-scheduling is turned on.",
                        ) {
                            DayTypeSegmentedControl(selectedDayType) { selectDayType(it) }
                        }

                        if (!energyBoundsValid) {
                            WebPlanCard(icon = WizardGlyph.CLOCK, iconTint = PlanHeaderGreen, title = "Nothing to preview yet", description = "Set a wake time and evening cutoff in Settings first.") {}
                        } else {
                            WebCard(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.padding(20.dp)) {
                                    DayPreviewTimeline(
                                        wakeMinutes = wakeMinutes!!,
                                        cutoffMinutes = cutoffMinutes!!,
                                        boundary1 = boundary1,
                                        boundary2 = boundary2,
                                        segmentLevels = segmentLevels,
                                        commitments = commitments,
                                        categories = categories,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("💡", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "This is just a preview based on your settings. Your tasks will appear here once auto-scheduling is turned on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebPlanCard(
    icon: WizardGlyph,
    iconTint: Color,
    title: String,
    description: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    WebCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    WizardIconGlyph(icon, iconTint, Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun WebFixedCommitmentRow(
    commitment: FixedCommitmentDraft,
    categories: List<TaskCategory>,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onChange: (FixedCommitmentDraft) -> Unit,
    onRemove: () -> Unit,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var subcategoryMenuExpanded by remember { mutableStateOf(false) }
    val linkedCategory = categories.find { it.id == commitment.categoryId }
    val linkedSubcategory = linkedCategory?.subcategories?.find { it.id == commitment.subcategoryId }
    val accent = linkedCategory?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = commitment.label,
                onValueChange = { onChange(commitment.copy(label = it)) },
                placeholder = { Text("e.g. Work, Tea") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { WizardIconGlyph(WizardGlyph.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(13.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = commitment.startTime,
                onValueChange = { onChange(commitment.copy(startTime = it)) },
                label = { Text("From") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = commitment.endTime,
                onValueChange = { onChange(commitment.copy(endTime = it)) },
                label = { Text("To") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (linkedCategory != null) accent.copy(alpha = 0.14f) else Color.White)
                        .border(1.dp, if (linkedCategory != null) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .clickable { categoryMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (linkedCategory != null) Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                    Text(linkedCategory?.name ?: "No category", style = MaterialTheme.typography.bodySmall, color = accent)
                }
                DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("No category") },
                        onClick = { onChange(commitment.copy(categoryId = null, subcategoryId = null)); categoryMenuExpanded = false },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            // Changing category invalidates any subcategory picked under the old one.
                            onClick = { onChange(commitment.copy(categoryId = category.id, subcategoryId = null)); categoryMenuExpanded = false },
                        )
                    }
                }
            }

            if (linkedCategory != null && linkedCategory.subcategories.isNotEmpty()) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (linkedSubcategory != null) accent.copy(alpha = 0.14f) else Color.White)
                            .border(1.dp, if (linkedSubcategory != null) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                            .clickable { subcategoryMenuExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(linkedSubcategory?.name ?: "No subcategory", style = MaterialTheme.typography.bodySmall, color = accent)
                    }
                    DropdownMenu(expanded = subcategoryMenuExpanded, onDismissRequest = { subcategoryMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("No subcategory") },
                            onClick = { onChange(commitment.copy(subcategoryId = null)); subcategoryMenuExpanded = false },
                        )
                        linkedCategory.subcategories.forEach { sub ->
                            DropdownMenuItem(
                                text = { Text(sub.name) },
                                onClick = { onChange(commitment.copy(subcategoryId = sub.id)); subcategoryMenuExpanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
