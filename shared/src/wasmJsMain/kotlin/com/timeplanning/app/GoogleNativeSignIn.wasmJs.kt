package com.timeplanning.app

import androidx.compose.runtime.Composable

/**
 * NOT IMPLEMENTED. There's no "native" sign-in SDK on the web — the real
 * equivalent would be Google Identity Services' JS popup flow, which is a
 * different integration entirely (client-side ID token, not a
 * server-exchangeable auth code) and out of scope for now. The
 * browser-redirect flow (ApiClient.fetchLoginUrl, opened via
 * rememberUrlOpener into a new tab) already works unmodified on web: it's
 * the same server callback used by Android/iOS's browser-fallback path.
 */
@Composable
actual fun rememberGoogleNativeSignIn(webClientId: String, onResult: (serverAuthCode: String?, error: String?) -> Unit): () -> Unit {
    return { onResult(null, "Use browser sign-in below") }
}
