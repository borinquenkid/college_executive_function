package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings

@Composable
actual fun rememberSettings(): Settings {
    return remember<Settings> { NSUserDefaultsSettings.Factory().create() }
}
