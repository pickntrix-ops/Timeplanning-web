package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The real (if still early) app: sign in once (session persisted — see
 * SessionStore), then a Today view with an Add Task flow. Not everything
 * from the design spec's wireframes yet (no Week view, no Habits/Stats
 * screen, no mood tracker) — see PROJECT_LOG.md for what's next.
 *
 * Web shares every screen with the app (same logic, same ApiClient calls)
 * but gets its own look here: a desktop-appropriate color scheme and a
 * width-capped, centered content panel instead of one screen's worth of
 * phone UI stretched full-bleed across a wide browser window.
 */
@Composable
@Preview
fun App() {
    val isWeb = remember { currentWebOrigin() != null }

    MaterialTheme(colorScheme = if (isWeb) WebColorScheme else AppColorScheme) {
        val apiClient = remember { ApiClient() }
        val sessionStore = rememberSessionStore()
        val scope = rememberCoroutineScope()

        var sessionToken by remember { mutableStateOf<String?>(null) }
        var loadedStoredSession by remember { mutableStateOf(false) }
        var screen by remember { mutableStateOf<Screen>(Screen.SignIn) }
        var categoriesRefreshKey by remember { mutableStateOf(0) }
        // Where AddTask returns to — the Tasks tab's + button opens it too, not just Today's FAB.
        var addTaskReturn by remember { mutableStateOf<Screen>(Screen.Today) }

        LaunchedEffect(Unit) {
            // A token in the URL means the web OAuth redirect (see
            // AuthController.callback) just completed — takes priority over
            // whatever's already stored, and is itself then stored.
            val urlToken = consumeOAuthTokenFromUrl()
            sessionToken = urlToken ?: sessionStore.load()
            if (urlToken != null) sessionStore.save(urlToken)
            loadedStoredSession = true
            if (sessionToken != null) screen = Screen.Today
        }

        if (!loadedStoredSession) return@MaterialTheme

        fun onSelectTab(tab: BottomTab) {
            screen = when (tab) {
                BottomTab.TODAY -> Screen.Today
                BottomTab.CALENDAR -> Screen.Calendar
                BottomTab.PLAN -> Screen.Plan
                BottomTab.CATEGORIES -> Screen.TaskCategories
                BottomTab.ACCOUNT -> Screen.Account
            }
        }

        val content: @Composable () -> Unit = {
        when (val current = screen) {
            is Screen.SignIn -> SignInScreen(apiClient) { token ->
                sessionStore.save(token)
                sessionToken = token
                screen = Screen.Today
            }

            is Screen.Today -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else {
                    TodayScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        currentTab = BottomTab.TODAY,
                        onSelectTab = ::onSelectTab,
                        onAddTask = { addTaskReturn = Screen.Today; screen = Screen.AddTask },
                        onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
                    )
                }
            }

            is Screen.AddTask -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebAddTaskScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onDone = { categoriesRefreshKey++; screen = addTaskReturn },
                        onCancel = { screen = addTaskReturn },
                    )
                } else {
                    AddTaskScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onDone = { categoriesRefreshKey++; screen = addTaskReturn },
                        onCancel = { screen = addTaskReturn },
                    )
                }
            }

            is Screen.EditTask -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebAddTaskScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        editingTask = current.task,
                        onDone = { categoriesRefreshKey++; screen = Screen.Today },
                        onCancel = { screen = Screen.Today },
                    )
                } else {
                    AddTaskScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        editingTask = current.task,
                        onDone = { screen = Screen.Today },
                        onCancel = { screen = Screen.Today },
                    )
                }
            }

            is Screen.TaskDetail -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebTaskDetailScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        task = current.task,
                        categoryColor = current.categoryColor,
                        onBack = { screen = Screen.Today },
                        onEdit = { task -> screen = Screen.EditTask(task) },
                        onDeleted = { screen = Screen.Today },
                    )
                } else {
                    TaskDetailScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        task = current.task,
                        categoryColor = current.categoryColor,
                        onBack = { screen = Screen.Today },
                        onEdit = { task -> screen = Screen.EditTask(task) },
                        onDeleted = { screen = Screen.Today },
                    )
                }
            }

            is Screen.Calendar -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else {
                    CalendarScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        currentTab = BottomTab.CALENDAR,
                        onSelectTab = ::onSelectTab,
                    )
                }
            }

            is Screen.Plan -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebPlanScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        currentTab = BottomTab.PLAN,
                        onSelectTab = ::onSelectTab,
                    )
                } else {
                    PlanScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        currentTab = BottomTab.PLAN,
                        onSelectTab = ::onSelectTab,
                    )
                }
            }

            is Screen.TaskCategories -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebCategoriesScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        screen = current,
                        currentTab = BottomTab.CATEGORIES,
                        onSelectTab = ::onSelectTab,
                        onNavigate = { screen = it },
                        refreshKey = categoriesRefreshKey,
                    )
                } else {
                    TaskCategoriesScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        currentTab = BottomTab.CATEGORIES,
                        onSelectTab = ::onSelectTab,
                        onOpenCategory = { category -> screen = Screen.CategoryDetail(category.id) },
                        onAddCategory = { screen = Screen.AddCategory },
                        onAddTask = { addTaskReturn = Screen.TaskCategories; screen = Screen.AddTask },
                        refreshKey = categoriesRefreshKey,
                    )
                }
            }

            is Screen.CategoryDetail -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebCategoriesScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        screen = current,
                        currentTab = BottomTab.CATEGORIES,
                        onSelectTab = ::onSelectTab,
                        onNavigate = { screen = it },
                        refreshKey = categoriesRefreshKey,
                    )
                } else {
                    CategoryDetailScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        categoryId = current.categoryId,
                        refreshKey = categoriesRefreshKey,
                        onBack = { screen = Screen.TaskCategories },
                        onEdit = { category -> screen = Screen.EditCategory(category) },
                        onDeleted = { categoriesRefreshKey++; screen = Screen.TaskCategories },
                        onViewTasks = { catId, subId -> screen = Screen.CategoryTasks(catId, subId) },
                        onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
                    )
                }
            }

            is Screen.CategoryTasks -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebCategoriesScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        screen = current,
                        currentTab = BottomTab.CATEGORIES,
                        onSelectTab = ::onSelectTab,
                        onNavigate = { screen = it },
                        refreshKey = categoriesRefreshKey,
                    )
                } else {
                    CategoryTasksScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        categoryId = current.categoryId,
                        subcategoryId = current.subcategoryId,
                        onBack = { screen = Screen.CategoryDetail(current.categoryId) },
                        onEdit = { category -> screen = Screen.EditCategory(category) },
                        onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
                    )
                }
            }

            is Screen.AddCategory -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebCategoryFormScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onDone = { categoriesRefreshKey++; screen = Screen.TaskCategories },
                        onCancel = { screen = Screen.TaskCategories },
                    )
                } else {
                    CategoryWizardScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onDone = { categoriesRefreshKey++; screen = Screen.TaskCategories },
                        onCancel = { screen = Screen.TaskCategories },
                    )
                }
            }

            is Screen.EditCategory -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    WebCategoryFormScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        editingCategory = current.category,
                        onDone = { categoriesRefreshKey++; screen = Screen.CategoryDetail(current.category.id) },
                        onCancel = { screen = Screen.CategoryDetail(current.category.id) },
                    )
                } else {
                    CategoryFormScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        editingCategory = current.category,
                        onDone = { categoriesRefreshKey++; screen = Screen.CategoryDetail(current.category.id) },
                        onCancel = { screen = Screen.CategoryDetail(current.category.id) },
                    )
                }
            }

            is Screen.BulkAddTasks -> {
                val token = sessionToken
                val back = if (current.subcategoryId != null) Screen.CategoryTasks(current.categoryId, current.subcategoryId) else Screen.CategoryDetail(current.categoryId)
                if (token == null) {
                    screen = Screen.SignIn
                } else if (isWeb) {
                    // Rendered inline by WebCategoriesScreen itself (right pane swaps to the
                    // bulk-add form, sidebar stays put) rather than navigating to its own page.
                    WebCategoriesScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        screen = current,
                        currentTab = BottomTab.CATEGORIES,
                        onSelectTab = ::onSelectTab,
                        onNavigate = { screen = it },
                        refreshKey = categoriesRefreshKey,
                    )
                } else {
                    // Web-only feature — nothing should navigate here on mobile.
                    screen = back
                }
            }

            is Screen.Account -> {
                AccountScreen(
                    currentTab = BottomTab.ACCOUNT,
                    onSelectTab = ::onSelectTab,
                    onSignOut = {
                        sessionStore.clear()
                        sessionToken = null
                        screen = Screen.SignIn
                    },
                )
            }
        }
        }

        // The categories section (browsing, forms, task detail) is genuinely
        // desktop UI on web — its own full-bleed master-detail shell and wide
        // page forms, not the mobile screens narrowed into the capped panel
        // below. Today/Calendar/Sign-in/Account still get that capped panel.
        val useWideWebLayout = isWeb && screen.let {
            it is Screen.TaskCategories || it is Screen.CategoryDetail || it is Screen.CategoryTasks ||
                it is Screen.AddCategory || it is Screen.EditCategory || it is Screen.BulkAddTasks ||
                it is Screen.AddTask || it is Screen.EditTask || it is Screen.TaskDetail || it is Screen.Plan
        }

        if (useWideWebLayout) {
            content()
        } else if (isWeb) {
            Box(
                modifier = Modifier.fillMaxSize().background(WebColorScheme.background).padding(vertical = 32.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().widthIn(max = WebContentMaxWidth.dp).fillMaxSize()
                        .background(WebColorScheme.surface)
                        .border(1.dp, WebColorScheme.outlineVariant),
                ) {
                    content()
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}
