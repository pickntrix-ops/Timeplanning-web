package com.timeplanning.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(apiClient: ApiClient, onSignedIn: (sessionToken: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlOpener()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pastedToken by remember { mutableStateOf("") }
    var showBrowserFallback by remember { mutableStateOf(false) }

    val launchNativeSignIn = rememberGoogleNativeSignIn(GOOGLE_WEB_CLIENT_ID) { code, nativeError ->
        if (code == null) {
            error = nativeError
            loading = false
            return@rememberGoogleNativeSignIn
        }
        scope.launch {
            runCatching { apiClient.exchangeNativeCode(code) }
                .onSuccess { onSignedIn(it.sessionToken) }
                .onFailure { error = "Sign-in failed: ${it.message}" }
            loading = false
        }
    }

    Column(
        modifier = Modifier.safeContentPadding().fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TimePlanning", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in to see your schedule.", style = MaterialTheme.typography.bodyMedium)

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = {
                loading = true
                error = null
                launchNativeSignIn()
            },
        ) {
            Text("Sign in with Google")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showBrowserFallback = !showBrowserFallback },
        ) {
            Text(if (showBrowserFallback) "Hide other sign-in option" else "Having trouble? Try browser sign-in")
        }

        if (showBrowserFallback) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching { apiClient.fetchLoginUrl(currentWebOrigin()) }
                            .onSuccess { openUrl(it) }
                            .onFailure { error = "Couldn't start sign-in: ${it.message}" }
                        loading = false
                    }
                },
            ) {
                Text("Open browser sign-in")
            }

            OutlinedTextField(
                value = pastedToken,
                onValueChange = { pastedToken = it },
                label = { Text("Paste session token from the browser") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = pastedToken.isNotBlank(),
                onClick = { onSignedIn(pastedToken.trim()) },
            ) {
                Text("Continue")
            }
        }

        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
