package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import com.borinquenterrier.cef.db.DriverFactory

expect val isDesktop: Boolean

@Composable
expect fun rememberDriverFactory(): DriverFactory
