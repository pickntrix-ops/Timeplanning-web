package com.timeplanning.app

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun rememberUrlOpener(): (String) -> Unit {
    return { url -> window.open(url, "_blank") }
}
