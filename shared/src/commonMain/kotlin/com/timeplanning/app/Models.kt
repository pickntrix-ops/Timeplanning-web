package com.timeplanning.app

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** A curated, distinct-hue palette for category colour — muted/dusty tones rather than neon-bright, kept small and deliberate rather than a full colour wheel. */
val CategoryPalette: List<String> = listOf(
    "#4B3F72", // dark purple (default)
    "#C6524A", // muted red
    "#D2953F", // muted amber
    "#3F6EA8", // muted blue
    "#6E4498", // muted violet
    "#268A66", // muted teal
    "#C06B34", // muted orange
    "#83828C", // grey
)

/** Parses a "#RRGGBB" hex string into a Compose Color; falls back to the first palette colour if malformed. */
fun String.toColorOrNull(): Color? = runCatching {
    val hex = removePrefix("#")
    require(hex.length == 6)
    Color(
        red = hex.substring(0, 2).toInt(16) / 255f,
        green = hex.substring(2, 4).toInt(16) / 255f,
        blue = hex.substring(4, 6).toInt(16) / 255f,
    )
}.getOrNull()

/** Deterministic fallback so a category created before colours existed (or left blank) still renders one consistently. Also reused by CalendarScreen to colour-code Google calendars, which have no colour field of their own. */
fun String.hashToPaletteColor(): String {
    val index = fold(0) { acc, c -> acc + c.code }.mod(CategoryPalette.size)
    return CategoryPalette[index]
}

/** A small, fixed icon set for categories — drawn in code (CategoryIconGlyph), not loaded from a library, matching this project's deliberate no-icon-dependency convention. */
enum class CategoryIcon {
    HOME, BRIEFCASE, DRINK, FITNESS, CARD, PEOPLE, BOOK, HEART, PLANE, CART, PAW, MORE;

    companion object {
        val default = HOME
    }
}

enum class TaskStatus { PENDING, SCHEDULED, COMPLETED, ABANDONED }

enum class RecurrenceUnit { D, W, M, Y }

enum class RecurrenceBase { DUE_DATE, COMPLETION_DATE }

/** A category's preferred energy level, for the not-yet-built plan/scheduling feature — null means "Any" (no constraint). */
enum class EnergyLevel { HIGH, MEDIUM, LOW }

fun EnergyLevel.label(): String = when (this) {
    EnergyLevel.HIGH -> "High"
    EnergyLevel.MEDIUM -> "Medium"
    EnergyLevel.LOW -> "Low"
}

/** Short, chip-friendly label — DUE_DATE/COMPLETION_DATE reads as jargon on its own. */
fun RecurrenceBase.shortLabel(): String = when (this) {
    RecurrenceBase.DUE_DATE -> "Fixed from due date"
    RecurrenceBase.COMPLETION_DATE -> "From completion date"
}

/** One line explaining what the short label means, with a concrete example — shown alongside it, not on its own. */
fun RecurrenceBase.description(): String = when (this) {
    RecurrenceBase.DUE_DATE -> "Next one lands on time regardless of when this one got done — good for bills and birthdays."
    RecurrenceBase.COMPLETION_DATE -> "Next one is counted from when you actually finish this — good for cleaning and chores."
}

@Serializable
data class TaskSubcategory(
    val id: Long,
    val name: String,
    val priority: Int = 0,
    val color: String? = null,
    // Each nullable: null = inherits the category's default, non-null overrides it for this subcategory.
    val ordered: Boolean? = null,
    val linksToPerson: Boolean? = null,
    val linksToEvent: Boolean? = null,
    val recurrenceBase: RecurrenceBase? = null,
) {
    /** color if set, else a colour deterministically derived from the name — never blank, same pattern as TaskCategory. */
    val displayColor: String get() = color ?: name.hashToPaletteColor()
}

/**
 * A user-defined replacement for the old fixed TaskType — see PROJECT_LOG.md
 * "task categories" entry. ordered/linksToPerson/linksToEvent are defaults;
 * any subcategory can override its own copy.
 */
@Serializable
data class TaskCategory(
    val id: Long,
    val name: String,
    val defaultRecurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val ordered: Boolean = false,
    val linksToPerson: Boolean = false,
    val linksToEvent: Boolean = false,
    val color: String? = null,
    val icon: String? = null,
    val description: String? = null,
    val energyLevel: EnergyLevel? = null,
    val weeklyHourCapMinutes: Int? = null,
    val autoSchedule: Boolean = true,
    val subcategories: List<TaskSubcategory> = emptyList(),
) {
    /** color if set, else a colour deterministically derived from the name — never blank. */
    val displayColor: String get() = color ?: name.hashToPaletteColor()

    /** icon if set and recognized, else a fixed default — never null, so a glyph always renders. */
    val displayIcon: CategoryIcon get() = icon?.let { raw -> runCatching { CategoryIcon.valueOf(raw) }.getOrNull() } ?: CategoryIcon.default
}

@Serializable
data class CreateTaskCategoryRequest(
    val name: String,
    val defaultRecurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val ordered: Boolean = false,
    val linksToPerson: Boolean = false,
    val linksToEvent: Boolean = false,
    val color: String? = null,
    val icon: String? = null,
    val description: String? = null,
    val energyLevel: EnergyLevel? = null,
    val weeklyHourCapMinutes: Int? = null,
    val autoSchedule: Boolean = true,
)

@Serializable
data class UpdateTaskCategoryRequest(
    val name: String? = null,
    val defaultRecurrenceBase: RecurrenceBase? = null,
    val ordered: Boolean? = null,
    val linksToPerson: Boolean? = null,
    val linksToEvent: Boolean? = null,
    val color: String? = null,
    val icon: String? = null,
    val clearDescription: Boolean = false,
    val description: String? = null,
    val clearEnergyLevel: Boolean = false,
    val energyLevel: EnergyLevel? = null,
    val clearWeeklyHourCapMinutes: Boolean = false,
    val weeklyHourCapMinutes: Int? = null,
    val autoSchedule: Boolean? = null,
)

@Serializable
data class SuggestEnergyLevelRequest(val name: String, val description: String? = null)

@Serializable
data class SuggestEnergyLevelResponse(val energyLevel: EnergyLevel?)

/** The two routine "shapes" a week is captured as — not per-specific-day. See DayProfile. */
enum class DayType { WEEKDAY, WEEKEND }

fun DayType.label(): String = when (this) {
    DayType.WEEKDAY -> "Weekday"
    DayType.WEEKEND -> "Weekend"
}

/**
 * One user's routine "shape" for a Weekday or a Weekend day, for the
 * in-progress plan/scheduling feature — wakeTime/eveningCutoff bound the
 * day, the three energy windows are where a category's own EnergyLevel gets
 * placed. Times are plain "HH:mm" (or "HH:mm:ss", the server's own format)
 * strings — every field but dayType is optional.
 */
/** A clock-anchored block that isn't a Google Calendar event but is still immovable — work hours, a fixed tea time. categoryId optionally ties it to the category it represents (e.g. a gym class → Exercise) — null if it isn't tied to one (e.g. "Work"). subcategoryId optionally narrows it further, within that category — null means either no category, or the category but no specific subcategory. */
@Serializable
data class FixedCommitment(
    val label: String,
    val startTime: String,
    val endTime: String,
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
)

@Serializable
data class DayProfile(
    val dayType: DayType,
    val wakeTime: String? = null,
    val eveningCutoff: String? = null,
    val highEnergyStart: String? = null,
    val highEnergyEnd: String? = null,
    val mediumEnergyStart: String? = null,
    val mediumEnergyEnd: String? = null,
    val lowEnergyStart: String? = null,
    val lowEnergyEnd: String? = null,
    val fixedCommitments: List<FixedCommitment> = emptyList(),
)

@Serializable
data class UpdateDayProfileRequest(
    val wakeTime: String? = null,
    val eveningCutoff: String? = null,
    val highEnergyStart: String? = null,
    val highEnergyEnd: String? = null,
    val mediumEnergyStart: String? = null,
    val mediumEnergyEnd: String? = null,
    val lowEnergyStart: String? = null,
    val lowEnergyEnd: String? = null,
    val fixedCommitments: List<FixedCommitment> = emptyList(),
)

/**
 * Not kotlinx.datetime.DayOfWeek — that type is compiled in a separate
 * library module without kotlinx.serialization's auto-generated enum
 * support, so using it directly in a @Serializable class fails to compile.
 * Names deliberately match it (and java.time.DayOfWeek, the server's own
 * type) exactly, since kotlinx.serialization serializes enums by name.
 */
enum class Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

fun Weekday.shortLabel(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/**
 * Specific days of the week (e.g. just Saturday) kept free of anything but
 * daily tasks — a concrete replacement for an earlier abstract "light
 * weekday/weekend" toggle, which couldn't tell Saturday from Sunday.
 */
@Serializable
data class FreeDaysResponse(val days: Set<Weekday>)

@Serializable
data class UpdateFreeDaysRequest(val days: Set<Weekday>)

/** A single, non-Weekday/Weekend-scoped setting — roughly how much of each day's real free time should stay unscheduled. See AppUser.freeTimePercent on the server. */
@Serializable
data class PlanPreferencesResponse(val freeTimePercent: Int)

@Serializable
data class UpdatePlanPreferencesRequest(val freeTimePercent: Int)

/** "07:30:00" (the server's own format) or "07:30" → "07:30", for display. Blank/malformed input passes through unchanged rather than throwing. */
fun String.toShortTimeOrSelf(): String = if (length >= 5) take(5) else this

/** A weekly-hour-cap field's stored minutes → the hours text shown/edited in the category form (e.g. 90 -> "1.5"), dropping a trailing ".0". */
fun Int.minutesToHoursText(): String {
    val hours = this / 60.0
    return if (hours == hours.toInt().toDouble()) hours.toInt().toString() else hours.toString()
}

/** The category form's typed hours text -> minutes to store, or null if blank/invalid. Accepts a decimal, e.g. "1.5" -> 90. */
fun String.hoursTextToMinutes(): Int? = trim().toDoubleOrNull()?.let { (it * 60).toInt() }

@Serializable
data class CreateTaskSubcategoryRequest(
    val name: String,
    val priority: Int = 0,
    val color: String? = null,
    val ordered: Boolean? = null,
    val linksToPerson: Boolean? = null,
    val linksToEvent: Boolean? = null,
    val recurrenceBase: RecurrenceBase? = null,
)

@Serializable
data class UpdateTaskSubcategoryRequest(
    val name: String? = null,
    val color: String? = null,
    val clearOrderedOverride: Boolean = false,
    val ordered: Boolean? = null,
    val clearLinksToPersonOverride: Boolean = false,
    val linksToPerson: Boolean? = null,
    val clearLinksToEventOverride: Boolean = false,
    val linksToEvent: Boolean? = null,
    val clearRecurrenceBaseOverride: Boolean = false,
    val recurrenceBase: RecurrenceBase? = null,
)

/** The category's own default, unless this subcategory explicitly overrides it. */
fun TaskSubcategory.effectiveRecurrenceBase(category: TaskCategory): RecurrenceBase =
    recurrenceBase ?: category.defaultRecurrenceBase

@Serializable
data class Task(
    val id: Long,
    val name: String,
    val taskCategoryId: Long,
    val taskCategoryName: String,
    val subcategoryId: Long? = null,
    val subcategoryName: String? = null,
    val personId: Long? = null,
    val personName: String? = null,
    val linkedEvent: String? = null,
    val dueDate: String? = null,
    val durationMinutes: Int,
    val isPinned: Boolean = false,
    val pinnedDay: String? = null,
    val status: TaskStatus,
    val recurrenceInterval: Int? = null,
    val recurrenceUnit: RecurrenceUnit? = null,
    val recurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val repeatsManually: Boolean = false,
    val followUpTaskId: Long? = null,
    val followUpTaskName: String? = null,
    val followUpOffsetDays: Int? = null,
    val rolloverCount: Int = 0,
    val queuePosition: Int? = null,
)

@Serializable
data class CreateTaskRequest(
    val name: String,
    val taskCategoryId: Long,
    val subcategoryId: Long? = null,
    val personId: Long? = null,
    val linkedEvent: String? = null,
    val dueDate: String? = null,
    val durationMinutes: Int,
    val recurrenceInterval: Int? = null,
    val recurrenceUnit: RecurrenceUnit? = null,
    val recurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val repeatsManually: Boolean = false,
    val followUpTaskId: Long? = null,
    val followUpOffsetDays: Int? = null,
    val queuePosition: Int? = null,
)

@Serializable
data class UpdateTaskRequest(
    val name: String? = null,
    val taskCategoryId: Long? = null,
    val subcategoryId: Long? = null,
    val personId: Long? = null,
    val linkedEvent: String? = null,
    val dueDate: String? = null,
    val durationMinutes: Int? = null,
    val recurrenceInterval: Int? = null,
    val recurrenceUnit: RecurrenceUnit? = null,
    val recurrenceBase: RecurrenceBase? = null,
    val repeatsManually: Boolean? = null,
    val followUpTaskId: Long? = null,
    val followUpOffsetDays: Int? = null,
    val queuePosition: Int? = null,
)

@Serializable
data class Person(
    val id: Long,
    val name: String,
    val birthday: String? = null,
)

@Serializable
data class CreatePersonRequest(val name: String)

@Serializable
data class GoogleCalendarInfo(
    val id: String,
    val summary: String,
    val primary: Boolean,
    val selected: Boolean,
)

@Serializable
data class CalendarEventInfo(
    val id: String,
    val calendarId: String,
    val calendarSummary: String,
    val title: String,
    val start: String,
    val end: String,
    val isAllDay: Boolean,
)

@Serializable
data class UpdateSelectedCalendarsRequest(val calendarIds: List<String>)
