package com.timeplanning.app

import androidx.compose.runtime.Composable

/**
 * Returns a function that launches the platform's native Google sign-in +
 * calendar-consent flow. On completion, onResult is called with either a
 * server auth code (to POST to /auth/google/token — see ApiClient) or an
 * error message. webClientId is the Web OAuth client ID (the server's own
 * client ID) — required by both platforms' SDKs so the resulting code is
 * exchangeable by the server, not just the device.
 */
@Composable
expect fun rememberGoogleNativeSignIn(webClientId: String, onResult: (serverAuthCode: String?, error: String?) -> Unit): () -> Unit
