package com.timeplanning.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

/**
 * The real (if still early) app: sign in once (session persisted — see
 * SessionStore), then a Today view with an Add Task flow. Not everything
 * from the design spec's wireframes yet (no Week view, no Habits/Stats
 * screen, no mood tracker) — see PROJECT_LOG.md for what's next.
 */
@Composable
@Preview
fun App() {
    MaterialTheme {
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

        when (screen) {
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
                        onAddTask = { screen = Screen.AddTask },
                        onOpenCalendar = { screen = Screen.Calendar },
                        onSignOut = {
                            sessionStore.clear()
                            sessionToken = null
                            screen = Screen.SignIn
                        },
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

            is Screen.Calendar -> {
                val token = sessionToken
                if (token == null) {
                    screen = Screen.SignIn
                } else {
                    CalendarScreen(
                        apiClient = apiClient,
                        sessionToken = token,
                        onBack = { screen = Screen.Today },
                    )
                }
            }
        }
    }
}
