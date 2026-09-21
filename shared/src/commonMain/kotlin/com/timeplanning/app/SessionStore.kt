package com.timeplanning.app

import androidx.compose.runtime.Composable

/**
 * Persists the session token across app restarts, so signing in isn't
 * needed every time. Android uses EncryptedSharedPreferences (it's a
 * credential, not a preference); iOS uses NSUserDefaults for now — the
 * Keychain would be the properly secure choice there, but this app can't be
 * run/tested on iOS Simulator on this machine at all (see PROJECT_LOG.md),
 * so it isn't worth guessing at Keychain API usage blind.
 */
@Composable
expect fun rememberSessionStore(): SessionStore

interface SessionStore {
    fun load(): String?
    fun save(token: String)
    fun clear()
}
