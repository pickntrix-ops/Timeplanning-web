package com.timeplanning.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private const val KEY_TOKEN = "session_token"

// NSUserDefaults, not Keychain — Keychain would be the properly secure
// choice for a credential like this, but can't be verified at all here
// (this app can't run on iOS Simulator on this machine — see
// PROJECT_LOG.md), so not worth guessing at Keychain Services API usage
// blind. Revisit once this can actually be tested on iOS.
@Composable
actual fun rememberSessionStore(): SessionStore = remember { IosSessionStore() }

private class IosSessionStore : SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): String? = defaults.stringForKey(KEY_TOKEN)
    override fun save(token: String) { defaults.setObject(token, KEY_TOKEN) }
    override fun clear() { defaults.removeObjectForKey(KEY_TOKEN) }
}
