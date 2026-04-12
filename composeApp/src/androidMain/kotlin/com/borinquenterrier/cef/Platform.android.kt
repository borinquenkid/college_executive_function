package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.borinquenterrier.cef.db.DriverFactory

actual val isDesktop: Boolean = false

@Composable
actual fun rememberDriverFactory(): DriverFactory {
    val context = LocalContext.current
    return remember(context) { DriverFactory(context) }
}
