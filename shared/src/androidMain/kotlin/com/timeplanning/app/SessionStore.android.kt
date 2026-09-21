package com.timeplanning.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_NAME = "timeplanning_session"
private const val KEY_TOKEN = "session_token"

@Composable
actual fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current
    return remember { AndroidSessionStore(context) }
}

private class AndroidSessionStore(context: Context) : SessionStore {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun load(): String? = prefs.getString(KEY_TOKEN, null)
    override fun save(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()
    override fun clear() = prefs.edit().remove(KEY_TOKEN).apply()
}
