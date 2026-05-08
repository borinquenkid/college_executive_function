package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.borinquenterrier.cef.db.DriverFactory


import android.util.Log
import com.borinquenterrier.college_executive_function.BuildConfig

actual val isDesktop: Boolean = false
actual val isDebug: Boolean = BuildConfig.DEBUG

@Composable
actual fun rememberModelDirectoryPath(): String {
    val context = LocalContext.current
    return remember(context) { context.filesDir.absolutePath }
}

@Composable
actual fun rememberDriverFactory(): DriverFactory {
    val context = LocalContext.current
    return remember(context) { DriverFactory(context) }
}

actual fun writeLogToFile(message: String) {
    if (!isDebug) return
    Log.d("CEF_DEBUG", message)
    // Writing to a file on Android can be handled via context, but for simple debug, 
    // Logcat is usually enough. For a persistent file:
    // This is a simplified version, as we don't have direct context here.
}
