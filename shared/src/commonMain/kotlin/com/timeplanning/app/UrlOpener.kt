package com.timeplanning.app

import androidx.compose.runtime.Composable

/** Opens a URL in the system browser — used for the Google sign-in redirect. */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit
