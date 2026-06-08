package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

@Composable
actual fun rememberSettings(): Settings {
    // Scope storage to this app's own package. Preferences.userNodeForPackage(Settings::class.java)
    // would resolve to com.russhwolf.settings's package instead, sharing one generic OS-level node
    // (and its contents, like the Gemini API key and OAuth tokens) with any other app on the
    // machine that uses the same library with that same mistaken pattern.
    val delegate = remember { Preferences.userRoot().node("com/borinquenterrier/cef") }
    return remember { PreferencesSettings(delegate) }
}
