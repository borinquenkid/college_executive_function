package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.borinquenterrier.cef.db.DriverFactory

actual val isDesktop: Boolean = false

@Composable
actual fun rememberDriverFactory(): DriverFactory {
    return remember { DriverFactory() }
}
