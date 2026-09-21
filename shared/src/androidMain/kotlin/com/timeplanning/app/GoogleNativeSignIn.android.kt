package com.timeplanning.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

private const val CALENDAR_READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

@Composable
actual fun rememberGoogleNativeSignIn(webClientId: String, onResult: (serverAuthCode: String?, error: String?) -> Unit): () -> Unit {
    val context = LocalContext.current

    val client = remember(webClientId) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // Same grant as the browser flow: identity + calendar in one
            // consent, and a server-exchangeable code (not just an on-device
            // token) since the scheduling engine needs offline access.
            .requestServerAuthCode(webClientId, /* forceCodeForRefreshToken = */ true)
            .requestScopes(Scope(CALENDAR_READONLY_SCOPE))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val code = account.serverAuthCode
            if (code != null) {
                onResult(code, null)
            } else {
                onResult(null, "Google didn't return a server auth code")
            }
        } catch (e: ApiException) {
            onResult(null, "Google sign-in failed (status ${e.statusCode})")
        }
    }

    return { launcher.launch(client.signInIntent) }
}
