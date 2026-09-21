package com.timeplanning.app

import androidx.compose.runtime.Composable

/**
 * NOT IMPLEMENTED. The real iOS version needs the GoogleSignIn-iOS SDK
 * (CocoaPods/SPM dependency in iosApp.xcodeproj) plus a URL scheme entry in
 * Info.plist for the OAuth redirect — none of which can be verified on this
 * machine, since Compose Multiplatform can't run in Simulator here at all
 * (Intel Mac, see PROJECT_LOG.md). Writing untested Xcode project/SDK
 * integration blind was judged worse than an honest stub. Use the
 * browser-redirect flow (ApiClient.fetchLoginUrl) on iOS for now.
 */
@Composable
actual fun rememberGoogleNativeSignIn(webClientId: String, onResult: (serverAuthCode: String?, error: String?) -> Unit): () -> Unit {
    return { onResult(null, "Native Google Sign-In isn't implemented on iOS yet — use the browser sign-in button instead") }
}
