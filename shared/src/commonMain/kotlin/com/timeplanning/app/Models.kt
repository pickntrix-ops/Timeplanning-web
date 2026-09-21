package com.timeplanning.app

import kotlinx.serialization.Serializable

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
    val subcategories: List<TaskSubcategory> = emptyList(),
)

@Serializable
data class CreateTaskCategoryRequest(
    val name: String,
    val defaultRecurrenceBase: RecurrenceBase = RecurrenceBase.DUE_DATE,
    val ordered: Boolean = false,
    val linksToPerson: Boolean = false,
    val linksToEvent: Boolean = false,
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
