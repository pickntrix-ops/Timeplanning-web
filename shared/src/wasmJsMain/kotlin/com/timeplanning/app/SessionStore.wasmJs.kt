package com.timeplanning.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.localStorage

private const val KEY_TOKEN = "timeplanning_session_token"

// localStorage, not a secure enclave — there isn't one on the web. Same
// tradeoff every web app with a bearer token makes; a proper fix would be a
// server-set httpOnly cookie instead of a token the JS app can read, which
// is follow-up work if this ever needs to resist XSS specifically.
@Composable
actual fun rememberSessionStore(): SessionStore = remember { WasmSessionStore() }

private class WasmSessionStore : SessionStore {
    override fun load(): String? = localStorage.getItem(KEY_TOKEN)
    override fun save(token: String) { localStorage.setItem(KEY_TOKEN, token) }
    override fun clear() { localStorage.removeItem(KEY_TOKEN) }
}
