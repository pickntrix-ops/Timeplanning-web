package com.timeplanning.app

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    return { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
