package com.timeplanning.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class PlanTab(val label: String) { SETTINGS("Settings"), PREVIEW("Preview") }

internal val PlanHeaderGreen = Color(0xFF0B5D46)
internal val EnergyHighColor = Color(0xFF34A853)
internal val EnergyMediumColor = Color(0xFFF2A93B)
internal val EnergyLowColor = Color(0xFFE0648C)

/** Local, editable form of FixedCommitment — a stable id so rows can be added/removed before anything's saved. */
internal data class FixedCommitmentDraft(
    val id: Int,
    val label: String,
    val startTime: String,
    val endTime: String,
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    /** MON..SUN codes; null means every day of the profile's day type. */
    val daysOfWeek: Set<String>? = null,
)

private val DayCodeLabels = mapOf("MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu", "FRI" to "Fri", "SAT" to "Sat", "SUN" to "Sun")

/** The days a commitment in this day type's profile can be limited to. */
internal fun DayType.dayCodes(): List<String> = when (this) {
    DayType.WEEKDAY -> listOf("MON", "TUE", "WED", "THU", "FRI")
    DayType.WEEKEND -> listOf("SAT", "SUN")
}

internal fun parseDayCodes(raw: String?): Set<String>? =
    raw?.split(",")?.map { it.trim().uppercase() }?.filter { it in DayCodeLabels }?.toSet()?.takeIf { it.isNotEmpty() }

/** What gets sent to the server — null (every day) when nothing's been narrowed, else the codes in week order. */
internal fun Set<String>?.toDaysOfWeekParam(dayType: DayType): String? {
    val all = dayType.dayCodes()
    if (this == null || all.all { it in this }) return null
    return all.filter { it in this }.joinToString(",").ifEmpty { null }
}

/** "Mon, Wed, Fri" — or null when it runs every day of the profile, so callers can just omit it. */
internal fun Set<String>?.daysSummary(dayType: DayType): String? =
    toDaysOfWeekParam(dayType)?.split(",")?.joinToString(", ") { DayCodeLabels.getValue(it) }

/** One toggle per day of this profile's day type. All on == every day (null); the last remaining day can't be switched off. */
@Composable
internal fun CommitmentDayPicker(dayType: DayType, selected: Set<String>?, accent: Color, onChange: (Set<String>?) -> Unit) {
    val all = dayType.dayCodes()
    val effective = selected?.filter { it in all }?.toSet()?.takeIf { it.isNotEmpty() } ?: all.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Which days?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            all.forEach { code ->
                val on = code in effective
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent)
                        .border(1.dp, if (on) accent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable {
                            val next = if (on) effective - code else effective + code
                            if (next.isNotEmpty()) onChange(if (next.size == all.size) null else next)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        DayCodeLabels.getValue(code).take(2),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                        color = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun timeToMinutes(hhmm: String): Int? {
    val parts = hhmm.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

internal fun minutesToTime(totalMinutes: Int): String {
    val m = totalMinutes.coerceIn(0, 24 * 60)
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

/** Nearest 10 minutes — used for the energy slider's fallback boundaries so an un-dragged default is never an odd value like "12:07". */
internal fun roundTo10(minutes: Int): Int = ((minutes + 5) / 10) * 10

internal fun EnergyLevel.color(): Color = when (this) {
    EnergyLevel.HIGH -> EnergyHighColor
    EnergyLevel.MEDIUM -> EnergyMediumColor
    EnergyLevel.LOW -> EnergyLowColor
}

internal fun EnergyLevel.previewLabel(): String = when (this) {
    EnergyLevel.HIGH -> "High energy time"
    EnergyLevel.MEDIUM -> "Medium energy time"
    EnergyLevel.LOW -> "Low energy time"
}

/**
 * Which energy level occupies which of the three slider positions (0 =
 * earliest in the day, 2 = latest) — NOT assumed to always be High→Medium→
 * Low in that order, since some people are highest energy in the evening,
 * lowest mid-afternoon, etc. Derived from whichever level's own start time
 * is earliest/middle/latest; falls back to High,Medium,Low when nothing's
 * set yet (blank start times), matching this screen's original default.
 */
internal fun deriveSegmentLevels(highStart: String, mediumStart: String, lowStart: String): List<EnergyLevel> {
    val entries = listOf(
        EnergyLevel.HIGH to (timeToMinutes(highStart) ?: 0),
        EnergyLevel.MEDIUM to (timeToMinutes(mediumStart) ?: 1),
        EnergyLevel.LOW to (timeToMinutes(lowStart) ?: 2),
    )
    return entries.sortedBy { it.second }.map { it.first }
}

/** Advances one slider position to the next level in High→Medium→Low→High order, swapping with whichever position currently holds that level — always keeps a valid one-to-one assignment. */
internal fun cycleSegmentLevels(levels: List<EnergyLevel>, position: Int): List<EnergyLevel> {
    val order = listOf(EnergyLevel.HIGH, EnergyLevel.MEDIUM, EnergyLevel.LOW)
    val current = levels[position]
    val next = order[(order.indexOf(current) + 1) % order.size]
    val swapWith = levels.indexOf(next)
    val updated = levels.toMutableList()
    updated[position] = next
    updated[swapWith] = current
    return updated
}

/** The stored end time for whichever named window (high/medium/low) this level is — used to read back a segment boundary once we know which level sits in that slider position. */
internal fun EnergyLevel.endTimeOf(highEnd: String, mediumEnd: String, lowEnd: String): String = when (this) {
    EnergyLevel.HIGH -> highEnd
    EnergyLevel.MEDIUM -> mediumEnd
    EnergyLevel.LOW -> lowEnd
}

/**
 * First piece of the plan/scheduling feature (see PROJECT_LOG.md's "plan"
 * design discussion): capturing (a) specific days of the week (e.g. just
 * Saturday) kept free of everything but daily tasks, and (b) a Weekday and a
 * Weekend routine "shape" — wake time, when the day should wind down, roughly
 * when the person has High/Medium/Low energy (as one continuous draggable
 * range, not six separate fields — the three windows are always contiguous
 * by construction: high-end == medium-start, medium-end == low-start), and
 * any clock-anchored fixed commitments (work hours, a set tea time) that
 * aren't in the person's calendar. Nothing here generates an actual
 * day-by-day plan yet; this is just the input the (not-yet-built) placement
 * algorithm will eventually read. Autosaves ~700ms after the last edit
 * (or immediately for Free days/on drag-end for the energy slider) rather
 * than a manual Save button.
 */
@Composable
fun PlanScreen(
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
    var expandedCommitmentId by remember { mutableStateOf<Int?>(null) }

    var freeDays by remember { mutableStateOf(setOf<Weekday>()) }
    var savingFreeDays by remember { mutableStateOf(false) }
    var freeTimePercent by remember { mutableStateOf("30") }
    var freeTimePercentTick by remember { mutableStateOf(0) }
    var preferWeekdays by remember { mutableStateOf(false) }
    var savingPreferWeekdays by remember { mutableStateOf(false) }
    var planTab by remember { mutableStateOf(PlanTab.SETTINGS) }

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
            FixedCommitmentDraft(i, c.label, c.startTime.toShortTimeOrSelf(), c.endTime.toShortTimeOrSelf(), c.categoryId, c.subcategoryId, parseDayCodes(c.daysOfWeek))
        }
        nextCommitmentId = commitments.size
        expandedCommitmentId = null
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

    // Writes a segment's time range back to whichever named window (high/medium/low) that level owns — used both
    // when dragging the slider (boundaries move, assignment stays) and when cycling a segment's label (assignment
    // moves, boundaries stay).
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
                            .map { FixedCommitment(it.label, it.startTime, it.endTime, it.categoryId, it.subcategoryId, it.daysOfWeek.toDaysOfWeekParam(selectedDayType)) },
                    ),
                )
            }.onSuccess { saved ->
                profiles = profiles + (selectedDayType to saved)
                savedJustNow = true
            }.onFailure { error = "Couldn't save: ${it.serverMessage()}" }
            saving = false
        }
    }

    // Any user-driven edit bumps changeTick (loadFieldsFrom/selectDayType deliberately don't,
    // so switching tabs or the initial load never triggers a save of what was just loaded).
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
        runCatching { apiClient.updatePlanPreferences(sessionToken, freeTimePercent = pct) }
            .onFailure { error = "Couldn't save: ${it.serverMessage()}" }
    }

    fun togglePreferWeekdays(value: Boolean) {
        val previous = preferWeekdays
        preferWeekdays = value
        savingPreferWeekdays = true
        scope.launch {
            runCatching { apiClient.updatePlanPreferences(sessionToken, preferWeekdays = value) }
                .onSuccess { preferWeekdays = it.preferWeekdays }
                .onFailure { preferWeekdays = previous; error = "Couldn't save: ${it.serverMessage()}" }
            savingPreferWeekdays = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { apiClient.fetchTaskCategories(sessionToken) }.onSuccess { categories = it }
        runCatching { apiClient.fetchFreeDays(sessionToken) }.onSuccess { freeDays = it }
        runCatching { apiClient.fetchPlanPreferences(sessionToken) }.onSuccess {
            freeTimePercent = it.freeTimePercent.toString()
            preferWeekdays = it.preferWeekdays
        }
        runCatching { apiClient.fetchDayProfiles(sessionToken) }
            .onSuccess { list ->
                profiles = list.associateBy { it.dayType }
                loadFieldsFrom(profiles[selectedDayType])
            }
            .onFailure { error = "Couldn't load your routine: ${it.serverMessage()}" }
        loading = false
    }

    // Shared by the Energy windows slider (Settings) and the timeline (Preview) — computed once so both read the same numbers.
    // Segment order (which level is earliest/middle/latest in the day) is derived from the levels' own start times,
    // not assumed to be High→Medium→Low — a night owl can have High energy in the last segment, for example.
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BottomNavBar(current = currentTab, onSelect = onSelectTab) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlanHeaderGreen, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Plan", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    if (saving || savedJustNow) {
                        Text(
                            if (saving) "Saving…" else "Saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                Text(
                    "Set your routine and availability so tasks can be planned around your life.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).padding(4.dp),
                ) {
                    PlanTab.entries.forEach { tab ->
                        val selected = tab == planTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) PlanHeaderGreen.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { planTab = tab }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                color = if (selected) PlanHeaderGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (planTab == PlanTab.PREVIEW) {
                    PlanSectionCard(
                        icon = WizardGlyph.CALENDAR,
                        iconTint = EnergyHighColor,
                        title = "Preview",
                        description = "This shows how your settings create available time during a typical ${selectedDayType.label().lowercase()}. Your actual tasks will appear here once auto-scheduling is turned on.",
                    ) {
                        DayTypeSegmentedControl(selectedDayType) { selectDayType(it) }
                    }

                    if (!energyBoundsValid) {
                        PlanSectionCard(icon = WizardGlyph.CLOCK, iconTint = PlanHeaderGreen, title = "Nothing to preview yet", description = "Set a wake time and evening cutoff in Settings first.") {}
                    } else {
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

                if (planTab == PlanTab.SETTINGS) {
                PlanSectionCard(
                    icon = WizardGlyph.CHECK_CIRCLE,
                    iconTint = EnergyHighColor,
                    title = "Free days",
                    description = "Choose any days you want to keep free from discretionary planning.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                PlanSectionCard(
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
                            modifier = Modifier.weight(1f),
                        )
                        Text("%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                PlanSectionCard(
                    icon = WizardGlyph.CALENDAR,
                    iconTint = PlanHeaderGreen,
                    title = "Weekday vs weekend",
                    description = "Where auto-scheduled tasks should land. \"Weekdays first\" only spills onto a weekend day once every weekday genuinely has no more room — good for keeping weekends quiet.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false to "Spread evenly", true to "Weekdays first").forEach { (value, label) ->
                            val selected = preferWeekdays == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) PlanHeaderGreen else Color.White)
                                    .border(1.dp, if (selected) PlanHeaderGreen else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !savingPreferWeekdays) { togglePreferWeekdays(value) }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                PlanSectionCard(
                    icon = WizardGlyph.CLOCK,
                    iconTint = PlanHeaderGreen,
                    title = "Your routine",
                    description = "Set up a typical weekday and weekend day. These are used to work out when things fit.",
                ) {
                    DayTypeSegmentedControl(selectedDayType) { selectDayType(it) }
                }

                PlanSectionCard(
                    icon = WizardGlyph.SUN,
                    iconTint = Color(0xFFE08A2B),
                    title = "Day bounds",
                    description = "Set when your day starts and when flexible tasks should stop.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlanTimeBox("Wake time", wakeTime, { wakeTime = it; markDirty() }, Modifier.weight(1f))
                        PlanTimeBox("Evening cutoff", eveningCutoff, { eveningCutoff = it; markDirty() }, Modifier.weight(1f))
                    }
                }

                PlanSectionCard(
                    icon = WizardGlyph.BOLT,
                    iconTint = Color(0xFFE08A2B),
                    title = "Energy windows",
                    description = "Drag the handles to set when you typically have each energy level during the day.",
                ) {
                    val wakeMinutes = timeToMinutes(wakeTime)
                    val cutoffMinutes = timeToMinutes(eveningCutoff)
                    if (wakeMinutes == null || cutoffMinutes == null || cutoffMinutes <= wakeMinutes) {
                        Text(
                            "Set your wake time and evening cutoff above first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val defaultB1 = roundTo10(wakeMinutes + (cutoffMinutes - wakeMinutes) / 3)
                        val defaultB2 = roundTo10(wakeMinutes + (cutoffMinutes - wakeMinutes) * 2 / 3)
                        val segments = deriveSegmentLevels(highStart, mediumStart, lowStart)
                        val boundary1 = timeToMinutes(segments[0].endTimeOf(highEnd, mediumEnd, lowEnd))?.coerceIn(wakeMinutes, cutoffMinutes) ?: defaultB1
                        val boundary2 = timeToMinutes(segments[1].endTimeOf(highEnd, mediumEnd, lowEnd))?.coerceIn(wakeMinutes, cutoffMinutes) ?: defaultB2

                        // Writes all three segments' times from a (possibly reordered) level assignment — a cycle
                        // swaps two positions at once, so both must be re-written to keep them contiguous.
                        fun applySegments(updated: List<EnergyLevel>) {
                            setLevelTimes(updated[0], wakeMinutes, boundary1)
                            setLevelTimes(updated[1], boundary1, boundary2)
                            setLevelTimes(updated[2], boundary2, cutoffMinutes)
                            markDirty()
                        }

                        EnergyRangeSlider(
                            startMinutes = wakeMinutes,
                            endMinutes = cutoffMinutes,
                            boundary1 = boundary1,
                            boundary2 = boundary2,
                            segmentLevels = segments,
                            onBoundariesChange = { b1, b2 ->
                                setLevelTimes(segments[0], wakeMinutes, b1)
                                setLevelTimes(segments[1], b1, b2)
                                setLevelTimes(segments[2], b2, cutoffMinutes)
                            },
                            onDragFinished = { markDirty() },
                        )

                        Text(
                            "Tap a label to change what energy level it represents.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EnergySummaryChip(segments[0], wakeMinutes, boundary1, onClick = { applySegments(cycleSegmentLevels(segments, 0)) }, modifier = Modifier.weight(1f))
                            EnergySummaryChip(segments[1], boundary1, boundary2, onClick = { applySegments(cycleSegmentLevels(segments, 1)) }, modifier = Modifier.weight(1f))
                            EnergySummaryChip(segments[2], boundary2, cutoffMinutes, onClick = { applySegments(cycleSegmentLevels(segments, 2)) }, modifier = Modifier.weight(1f))
                        }
                    }
                }

                PlanSectionCard(
                    icon = WizardGlyph.CALENDAR,
                    iconTint = EnergyHighColor,
                    title = "Fixed commitments",
                    description = "Add regular commitments that aren't in your calendar, like work, meals or classes.",
                ) {
                    commitments.forEach { commitment ->
                        FixedCommitmentItem(
                            commitment = commitment,
                            dayType = selectedDayType,
                            categories = categories,
                            expanded = expandedCommitmentId == commitment.id,
                            onToggleExpanded = { expandedCommitmentId = if (expandedCommitmentId == commitment.id) null else commitment.id },
                            onChange = { updated -> commitments = commitments.map { if (it.id == commitment.id) updated else it }; markDirty() },
                            onRemove = { commitments = commitments.filter { it.id != commitment.id }; expandedCommitmentId = null; markDirty() },
                        )
                    }
                    TextButton(onClick = {
                        val id = nextCommitmentId
                        commitments = commitments + FixedCommitmentDraft(id, "", "", "")
                        nextCommitmentId++
                        expandedCommitmentId = id
                    }) {
                        Text("+ Add a fixed commitment", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                } // planTab == PlanTab.SETTINGS

                Box(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PlanSectionCard(
    icon: WizardGlyph,
    iconTint: Color,
    title: String,
    description: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(iconTint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                WizardIconGlyph(icon, iconTint, Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WizardIconGlyph(WizardGlyph.INFO, MaterialTheme.colorScheme.outline, Modifier.size(18.dp))
        }
        content()
    }
}

@Composable
internal fun DayTypeSegmentedControl(selected: DayType, onSelect: (DayType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        DayType.entries.forEach { dayType ->
            val isSelected = dayType == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) PlanHeaderGreen.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(dayType) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dayType.label(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    color = if (isSelected) PlanHeaderGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A proportionally-scaled read-only vertical day timeline — High/Medium/Low
 * energy bands sized to their actual duration, with fixed commitments
 * overlaid at their real position within the day. Not an editor (see
 * EnergyRangeSlider for that) — this is purely "what does this add up to."
 */
@Composable
internal fun DayPreviewTimeline(
    wakeMinutes: Int,
    cutoffMinutes: Int,
    boundary1: Int,
    boundary2: Int,
    segmentLevels: List<EnergyLevel>,
    commitments: List<FixedCommitmentDraft>,
    categories: List<TaskCategory>,
) {
    val dpPerMinute = 1.0.dp
    val totalMinutes = (cutoffMinutes - wakeMinutes).coerceAtLeast(1)
    val totalHeight = dpPerMinute * totalMinutes
    fun yFor(minutes: Int) = dpPerMinute * (minutes - wakeMinutes).coerceIn(0, totalMinutes)

    val validCommitments = commitments.mapNotNull { c ->
        val start = timeToMinutes(c.startTime)?.coerceIn(wakeMinutes, cutoffMinutes)
        val end = timeToMinutes(c.endTime)?.coerceIn(wakeMinutes, cutoffMinutes)
        if (start != null && end != null && end > start && c.label.isNotBlank()) Triple(c, start, end) else null
    }

    val labelPoints = (setOf(wakeMinutes, boundary1, boundary2, cutoffMinutes) + validCommitments.flatMap { (_, s, e) -> listOf(s, e) })
        .filter { it in wakeMinutes..cutoffMinutes }
        .sorted()

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(44.dp).height(totalHeight)) {
            labelPoints.forEach { m ->
                Text(
                    minutesToTime(m),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(y = yFor(m) - 7.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).height(totalHeight)) {
            EnergyPreviewBlock(segmentLevels[0], wakeMinutes, boundary1, ::yFor, dpPerMinute)
            EnergyPreviewBlock(segmentLevels[1], boundary1, boundary2, ::yFor, dpPerMinute)
            EnergyPreviewBlock(segmentLevels[2], boundary2, cutoffMinutes, ::yFor, dpPerMinute)

            validCommitments.forEach { (commitment, start, end) ->
                val category = categories.find { it.id == commitment.categoryId }
                val accent = category?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier
                        .offset(y = yFor(start))
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .height(dpPerMinute * (end - start))
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (category != null) {
                            CategoryIconGlyph(category.displayIcon, accent, Modifier.size(13.dp))
                        } else {
                            WizardIconGlyph(WizardGlyph.CALENDAR, accent, Modifier.size(12.dp))
                        }
                        Text(commitment.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Text("${minutesToTime(start)} – ${minutesToTime(end)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EnergyPreviewBlock(level: EnergyLevel, startMinutes: Int, endMinutes: Int, yFor: (Int) -> Dp, dpPerMinute: Dp) {
    if (endMinutes <= startMinutes) return
    val color = level.color()
    Column(
        modifier = Modifier
            .offset(y = yFor(startMinutes))
            .fillMaxWidth()
            .height(dpPerMinute * (endMinutes - startMinutes))
            .background(color.copy(alpha = 0.16f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(level.previewLabel(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color)
        Text("${minutesToTime(startMinutes)} – ${minutesToTime(endMinutes)}", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
    }
}

@Composable
private fun PlanTimeBox(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WizardIconGlyph(WizardGlyph.CLOCK, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(13.dp))
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("--:--", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun EnergySummaryChip(level: EnergyLevel, startMinutes: Int, endMinutes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = level.color()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(level.label(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            WizardIconGlyph(WizardGlyph.REPEAT, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(10.dp))
        }
        Text("${minutesToTime(startMinutes)} – ${minutesToTime(endMinutes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One continuous track from startMinutes to endMinutes, split into three
 * coloured segments (High/Medium/Low) by two draggable handles — the
 * segments are always contiguous by construction, so this can't produce a
 * gap or overlap the way six free-text fields could.
 */
@Composable
internal fun EnergyRangeSlider(
    startMinutes: Int,
    endMinutes: Int,
    boundary1: Int,
    boundary2: Int,
    segmentLevels: List<EnergyLevel>,
    onBoundariesChange: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val totalRange = (endMinutes - startMinutes).coerceAtLeast(1)
    val minGapMinutes = 20
    val color0 = segmentLevels[0].color()
    val color1 = segmentLevels[1].color()
    val color2 = segmentLevels[2].color()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val handleRadiusPx = with(density) { 12.dp.toPx() }

        fun minutesToX(minutes: Int): Float = ((minutes - startMinutes).toFloat() / totalRange) * widthPx

        // Handles drag smoothly at minute precision internally, but the time it snaps to (and reports/displays)
        // is always a round 10 minutes — avoids odd values like "13:03" from a fine-grained drag.
        fun snapTo10(minutes: Float): Int = (minutes / 10f).roundToInt() * 10

        val trackHeight = 10.dp
        Canvas(modifier = Modifier.fillMaxWidth().height(trackHeight).align(Alignment.Center)) {
            val w = size.width
            val f1 = (minutesToX(boundary1) / w).coerceIn(0f, 1f)
            val f2 = (minutesToX(boundary2) / w).coerceIn(0f, 1f)
            val brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to color0,
                    f1 to color0,
                    f1 to color1,
                    f2 to color1,
                    f2 to color2,
                    1f to color2,
                ),
            )
            drawRoundRect(brush = brush, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }

        var dragB1 by remember(boundary1) { mutableStateOf(boundary1.toFloat()) }
        var dragB2 by remember(boundary2) { mutableStateOf(boundary2.toFloat()) }

        EnergyHandle(
            modifier = Modifier.align(Alignment.CenterStart),
            xPx = minutesToX(snapTo10(dragB1)),
            radiusPx = handleRadiusPx,
            color = color0,
            onDrag = { deltaPx ->
                val deltaMinutes = (deltaPx / widthPx) * totalRange
                dragB1 = (dragB1 + deltaMinutes).coerceIn((startMinutes + minGapMinutes).toFloat(), dragB2 - minGapMinutes)
                onBoundariesChange(snapTo10(dragB1), snapTo10(dragB2))
            },
            onDragEnd = onDragFinished,
        )
        EnergyHandle(
            modifier = Modifier.align(Alignment.CenterStart),
            xPx = minutesToX(snapTo10(dragB2)),
            radiusPx = handleRadiusPx,
            color = color2,
            onDrag = { deltaPx ->
                val deltaMinutes = (deltaPx / widthPx) * totalRange
                dragB2 = (dragB2 + deltaMinutes).coerceIn(dragB1 + minGapMinutes, (endMinutes - minGapMinutes).toFloat())
                onBoundariesChange(snapTo10(dragB1), snapTo10(dragB2))
            },
            onDragEnd = onDragFinished,
        )
    }
}

@Composable
private fun EnergyHandle(modifier: Modifier, xPx: Float, radiusPx: Float, color: Color, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val sizeDp = with(LocalDensity.current) { (radiusPx * 2).toDp() }
    Box(
        modifier = modifier
            .offset { IntOffset((xPx - radiusPx).roundToInt(), 0) }
            .size(sizeDp)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, color, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
    )
}

@Composable
private fun FixedCommitmentItem(
    commitment: FixedCommitmentDraft,
    dayType: DayType,
    categories: List<TaskCategory>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChange: (FixedCommitmentDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val linkedCategory = categories.find { it.id == commitment.categoryId }
    val linkedSubcategory = linkedCategory?.subcategories?.find { it.id == commitment.subcategoryId }
    val accent = linkedCategory?.displayColor?.toColorOrNull() ?: MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                if (linkedCategory != null) {
                    CategoryIconGlyph(linkedCategory.displayIcon, accent, Modifier.size(17.dp))
                } else {
                    WizardIconGlyph(WizardGlyph.CALENDAR, accent, Modifier.size(16.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    commitment.label.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    listOfNotNull(
                        commitment.daysOfWeek.daysSummary(dayType),
                        if (commitment.startTime.isNotBlank() && commitment.endTime.isNotBlank()) "${commitment.startTime} – ${commitment.endTime}" else "Set a time",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                linkedSubcategory?.name ?: linkedCategory?.name ?: "No category",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            var categoryMenuExpanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = commitment.label,
                    onValueChange = { onChange(commitment.copy(label = it)) },
                    placeholder = { Text("e.g. Work, Tea") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanTimeField("From", commitment.startTime, { onChange(commitment.copy(startTime = it)) }, Modifier.weight(1f))
                    PlanTimeField("To", commitment.endTime, { onChange(commitment.copy(endTime = it)) }, Modifier.weight(1f))
                }
                CommitmentDayPicker(dayType, commitment.daysOfWeek, MaterialTheme.colorScheme.primary) { onChange(commitment.copy(daysOfWeek = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (linkedCategory != null) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { categoryMenuExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                        var subcategoryMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (linkedSubcategory != null) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
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
                TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun PlanTimeField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("HH:mm") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}
