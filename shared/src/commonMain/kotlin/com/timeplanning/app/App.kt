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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

        var sessionToken by remember { mutableStateOf<String?>(null) }
        var loadedStoredSession by remember { mutableStateOf(false) }
        var screen by remember { mutableStateOf<Screen>(Screen.SignIn) }

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
                        onAddTask = { screen = Screen.AddTask },
                        onOpenTask = { task, color -> screen = Screen.TaskDetail(task, color) },
                    )
                }
            }

            is Screen.AddTask -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else {
                    AddTaskScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onDone = { screen = Screen.Today },
                        onCancel = { screen = Screen.Today },
                    )
                }
            }

            is Screen.EditTask -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
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

            is Screen.TaskCategories -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else {
                    TaskCategoriesScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        currentTab = BottomTab.CATEGORIES,
                        onSelectTab = ::onSelectTab,
                    )
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

        if (isWeb) {
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
            content()
        }
    }
}
