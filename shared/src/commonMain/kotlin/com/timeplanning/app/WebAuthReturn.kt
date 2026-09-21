package com.timeplanning.app

/** The current web origin (scheme://host[:port]) — null on Android/iOS, where the concept doesn't apply. */
expect fun currentWebOrigin(): String?

/**
 * If the page was just loaded with a `?token=...` query param (the server's
 * OAuth callback redirecting back after a completed sign-in — see
 * AuthController.callback), consumes it: returns the token and strips it
 * from the URL so a later refresh doesn't resubmit it. Null on Android/iOS,
 * or on web when there's no token in the URL.
 */
expect fun consumeOAuthTokenFromUrl(): String?
