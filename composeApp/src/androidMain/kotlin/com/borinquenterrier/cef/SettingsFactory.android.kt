package com.borinquenterrier.cef

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

@Composable
actual fun rememberSettings(): Settings {
    val context: Context = LocalContext.current
    return remember {
        SharedPreferencesSettings(
            context.getSharedPreferences(
                "app_settings",
                Context.MODE_PRIVATE
            )
        )
    }
}
