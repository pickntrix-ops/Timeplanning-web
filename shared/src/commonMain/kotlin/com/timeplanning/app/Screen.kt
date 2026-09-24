package com.timeplanning.app

/** Simple state-based navigation — no nav library, the screen set is small enough not to need one yet. */
sealed class Screen {
    data object SignIn : Screen()
    data object Today : Screen()
    data object AddTask : Screen()
    data class EditTask(val task: Task) : Screen()
    data class TaskDetail(val task: Task, val categoryColor: String?) : Screen()
    data object Calendar : Screen()
    data object TaskCategories : Screen()
    data class CategoryDetail(val categoryId: Long) : Screen()
    data object Account : Screen()
}

/** The four persistent bottom-nav destinations — Add/Edit Task and Task Detail sit on top of these as modal-style overlays, so they don't get a tab. */
enum class BottomTab(val label: String) {
    TODAY("Today"),
    CALENDAR("Calendar"),
    CATEGORIES("Tasks"),
    ACCOUNT("Account"),
}
