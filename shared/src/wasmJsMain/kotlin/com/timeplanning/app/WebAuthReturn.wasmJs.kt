package com.timeplanning.app

import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

actual fun currentWebOrigin(): String? = window.location.origin

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun consumeOAuthTokenFromUrl(): String? {
    val search = window.location.search
    if (search.isEmpty() || search == "?") return null

    val params = URLSearchParams(search.toJsString())
    val token = params.get("token") ?: return null

    params.delete("token")
    val newSearch = params.toString()
    val newUrl = window.location.pathname + (if (newSearch.isNotEmpty()) "?$newSearch" else "") + window.location.hash
    window.history.replaceState(null, "", newUrl)
    return token
}
