package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

@Composable
actual fun rememberSettings(): Settings {
    val delegate = remember { Preferences.userNodeForPackage(Settings::class.java) }
    return remember { PreferencesSettings(delegate) }
}
