package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import com.borinquenterrier.cef.db.DriverFactory

expect val isDebug: Boolean
expect val platformName: String

@Composable
expect fun rememberModelDirectoryPath(): String

@Composable
expect fun rememberDriverFactory(): DriverFactory

