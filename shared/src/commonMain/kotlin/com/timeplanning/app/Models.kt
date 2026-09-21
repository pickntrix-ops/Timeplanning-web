package com.timeplanning.app

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** A curated, distinct-hue palette for category colour — kept small and deliberate rather than a full colour wheel. */
val CategoryPalette: List<String> = listOf(
    "#5B4FE0", // indigo
    "#2A9D8F", // teal
    "#E76F51", // coral
    "#F4A825", // amber
    "#D6336C", // rose
    "#3B82C4", // sky
    "#52A447", // green
    "#8854D0", // purple
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

/** Deterministic fallback so a category created before colours existed (or left blank) still renders one consistently. */
private fun String.hashToPaletteColor(): String {
    val index = fold(0) { acc, c -> acc + c.code }.mod(CategoryPalette.size)
    return CategoryPalette[index]
}

enum class TaskStatus { PENDING, SCHEDULED, COMPLETED, ABANDONED }

enum class RecurrenceUnit { D, W, M, Y }

enum class RecurrenceBase { DUE_DATE, COMPLETION_DATE }

/** Plain-English label for the recurrence-style pickers — DUE_DATE/COMPLETION_DATE reads as jargon on its own. */
fun RecurrenceBase.label(): String = when (this) {
    RecurrenceBase.DUE_DATE -> "Fixed schedule (stays on rhythm even if you're late)"
    RecurrenceBase.COMPLETION_DATE -> "Restarts from completion (counts from when you actually finish it)"
}

@Serializable
data class TaskSubcategory(
    val id: Long,
    val name: String,
    val priority: Int = 0,
    // Each nullable: null = inherits the category's default, non-null overrides it for this subcategory.
    val ordered: Boolean? = null,
    val linksToPerson: Boolean? = null,
    val linksToEvent: Boolean? = null,
)

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
    val subcategories: List<TaskSubcategory> = emptyList(),
) {
    /** color if set, else a colour deterministically derived from the name — never blank. */
    val displayColor: String get() = color ?: name.hashToPaletteColor()
}

@Serializable
data class CreateTaskCategoryRequest(
    val name: String,
    val defaultRecurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val ordered: Boolean = false,
    val linksToPerson: Boolean = false,
    val linksToEvent: Boolean = false,
    val color: String? = null,
)

@Serializable
data class UpdateTaskCategoryRequest(
    val name: String? = null,
    val defaultRecurrenceBase: RecurrenceBase? = null,
    val ordered: Boolean? = null,
    val linksToPerson: Boolean? = null,
    val linksToEvent: Boolean? = null,
    val color: String? = null,
)

@Serializable
data class CreateTaskSubcategoryRequest(
    val name: String,
    val priority: Int = 0,
    val ordered: Boolean? = null,
    val linksToPerson: Boolean? = null,
    val linksToEvent: Boolean? = null,
)

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
