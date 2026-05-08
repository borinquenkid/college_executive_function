package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import com.borinquenterrier.cef.db.DriverFactory

expect val isDesktop: Boolean
expect val isDebug: Boolean

@Composable
expect fun rememberModelDirectoryPath(): String

@Composable
expect fun rememberDriverFactory(): DriverFactory

