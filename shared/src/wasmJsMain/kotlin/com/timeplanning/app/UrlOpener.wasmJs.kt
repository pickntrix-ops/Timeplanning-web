package com.timeplanning.app

import androidx.compose.runtime.Composable
import kotlinx.browser.window

// Same-tab navigation, not a new tab: /callback redirects back to
// currentWebOrigin() with the session token once sign-in completes (see
// AuthController.callback / consumeOAuthTokenFromUrl), and that only lands
// somewhere useful if it's this tab that navigated away in the first place.
@Composable
actual fun rememberUrlOpener(): (String) -> Unit {
    return { url -> window.location.href = url }
}
