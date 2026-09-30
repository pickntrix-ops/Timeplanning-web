package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

    // The phone uses the web's palette and square style too (LocalWebStyle), so both look the same.
    MaterialTheme(colorScheme = WebColorScheme) {
    CompositionLocalProvider(LocalWebStyle provides true) {
        val apiClient = remember { ApiClient() }
        val sessionStore = rememberSessionStore()
        val scope = rememberCoroutineScope()

        var sessionToken by remember { mutableStateOf<String?>(null) }
        var loadedStoredSession by remember { mutableStateOf(false) }
        var screen by remember { mutableStateOf<Screen>(Screen.SignIn) }
        var categoriesRefreshKey by remember { mutableStateOf(0) }
        // Where AddTask returns to — the Tasks tab's + button opens it too, not just Today's FAB.
        var addTaskReturn by remember { mutableStateOf<Screen>(Screen.Today) }
        // Web only: the new/edit task form is a panel sliding in over the current page, not a screen of its own.
        var taskPanelVisible by remember { mutableStateOf(false) }
        var taskPanelEditing by remember { mutableStateOf<Task?>(null) }
        var taskPanelKey by remember { mutableStateOf(0) }
        /** Bumped after the panel saves, so the page underneath reloads and shows the change. */
        var contentReloadKey by remember { mutableStateOf(0) }
        fun openTaskPanel(editing: Task?) {
            taskPanelEditing = editing
            taskPanelKey++
            taskPanelVisible = true
        }

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

        if (!loadedStoredSession) return@CompositionLocalProvider

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
                } else if (isWeb) {
                    WebTodayScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
                    )
                } else {
                    // The web's Today, stacked for a phone, with the usual bottom tab bar.
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { BottomNavBar(current = BottomTab.TODAY, onSelect = ::onSelectTab) },
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            WebTodayScreen(
                                apiClient = apiClient,
                                sessionToken = token,
                                onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
                                compact = true,
                                onAddTask = { addTaskReturn = Screen.Today; screen = Screen.AddTask },
                            )
                        }
                    }
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
                    // Same form as the web's task panel, full screen.
                    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
                        WebAddTaskScreen(
                            apiClient = apiClient,
                            sessionToken = token,
                            onDone = { categoriesRefreshKey++; screen = addTaskReturn },
                            onCancel = { screen = addTaskReturn },
                        )
                    }
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
                    Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
                        WebAddTaskScreen(
                            apiClient = apiClient,
                            sessionToken = token,
                            editingTask = current.task,
                            onDone = { categoriesRefreshKey++; screen = Screen.Today },
                            onCancel = { screen = Screen.Today },
                        )
                    }
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
                        onEdit = { task -> openTaskPanel(task) },
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
                } else if (isWeb) {
                    WebCalendarScreen(apiClient = apiClient, sessionToken = token)
                } else {
                    // The web's week calendar, compact: two days on screen, swipe for the rest.
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = { BottomNavBar(current = BottomTab.CALENDAR, onSelect = ::onSelectTab) },
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            WebCalendarScreen(apiClient = apiClient, sessionToken = token, compact = true)
                        }
                    }
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
                        onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
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
                it is Screen.AddTask || it is Screen.EditTask || it is Screen.TaskDetail || it is Screen.Plan || it is Screen.Calendar || it is Screen.Today
        }

        val webToken = sessionToken
        if (isWeb && webToken != null && screen != Screen.SignIn) {
            // Every signed-in web page sits beside the persistent sidebar (WebSidebar); the screens'
            // own bottom bar / top nav hide themselves inside it (LocalInWebShell).
            CompositionLocalProvider(LocalInWebShell provides true) {
                Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().background(WebColorScheme.background)) {
                    WebSidebar(
                        apiClient = apiClient,
                        sessionToken = webToken,
                        current = webTabFor(screen),
                        refreshKey = categoriesRefreshKey,
                        onSelect = ::onSelectTab,
                        onNewTask = { openTaskPanel(null) },
                        onOpenCategory = { screen = Screen.CategoryDetail(it) },
                        onAddCategory = { screen = Screen.AddCategory },
                        onSignOut = {
                            sessionStore.clear()
                            sessionToken = null
                            screen = Screen.SignIn
                        },
                    )
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        key(contentReloadKey) {
                            if (useWideWebLayout) {
                                content()
                            } else {
                                Box(Modifier.fillMaxHeight().widthIn(max = WebContentMaxWidth.dp).fillMaxWidth().padding(vertical = 16.dp)) { content() }
                            }
                        }
                    }
                }
                // The task panel: a dimmed page and a white panel sliding in from the right.
                AnimatedVisibility(visible = taskPanelVisible, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { taskPanelVisible = false },
                    )
                }
                AnimatedVisibility(
                    visible = taskPanelVisible,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Box(Modifier.fillMaxHeight().width(440.dp).shadow(24.dp)) {
                        key(taskPanelKey) {
                            WebAddTaskScreen(
                                apiClient = apiClient,
                                sessionToken = webToken,
                                editingTask = taskPanelEditing,
                                onDone = {
                                    categoriesRefreshKey++
                                    taskPanelVisible = false
                                    // An edited task's detail page holds the old copy — go back to Today; otherwise refresh in place.
                                    if (taskPanelEditing != null && screen is Screen.TaskDetail) screen = Screen.Today else contentReloadKey++
                                },
                                onCancel = { taskPanelVisible = false },
                            )
                        }
                    }
                }
                }
            }
        } else if (isWeb) {
            Box(
                modifier = Modifier.fillMaxSize().background(WebColorScheme.background).padding(vertical = 32.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(modifier = Modifier.fillMaxHeight().widthIn(max = WebContentMaxWidth.dp).fillMaxSize()) { content() }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
    }
}

/** Which sidebar row a web page belongs under — null for pages that aren't part of one section (adding/viewing a single task). */
private fun webTabFor(screen: Screen): BottomTab? = when (screen) {
    Screen.Today -> BottomTab.TODAY
    Screen.Calendar -> BottomTab.CALENDAR
    Screen.Plan -> BottomTab.PLAN
    Screen.Account -> BottomTab.ACCOUNT
    Screen.TaskCategories, is Screen.CategoryDetail, is Screen.CategoryTasks, Screen.AddCategory,
    is Screen.EditCategory, is Screen.BulkAddTasks -> BottomTab.CATEGORIES
    else -> null
}
