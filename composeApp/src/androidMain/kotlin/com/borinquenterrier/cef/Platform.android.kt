package com.borinquenterrier.cef


import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.borinquenterrier.cef.db.DriverFactory
import com.borinquenterrier.college_executive_function.BuildConfig
import java.io.File
import java.util.Date

object AndroidAppContext {
    var applicationContext: Context? = null
}

actual val isDesktop: Boolean = false
actual val isDebug: Boolean = BuildConfig.DEBUG
actual val platformName: String = "Android"

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

// Caps the debug log file so it can't grow unbounded across a long-running session.
// The cap/trim logic itself lives in commonMain's LogFileCap.kt, shared with iOS and desktop.
private const val LOG_FILE_NAME = "cef_debug_log.txt"

/**
 * Persists debug log lines to a file under the app's internal files directory, retrievable
 * afterward via `adb pull`/Device File Explorer without needing a live Logcat session —
 * useful for diagnosing anything found on a release build. Previously this was Logcat-only
 * despite the name/comment claiming otherwise, even though AndroidAppContext.applicationContext
 * is available right here (matches the same gap fixed on iOS's Platform.ios.kt).
 */
actual fun writeLogToFile(message: String) {
    if (!isDebug) return
    Log.d("CEF_DEBUG", message)

    try {
        val context = AndroidAppContext.applicationContext ?: return
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        logFile.appendText("[${Date()}] $message\n")
        if (logFile.length() > MAX_LOG_FILE_BYTES) {
            logFile.writeText(capLogContent(logFile.readText()))
        }
    } catch (e: Exception) {
        Log.w("CEF_DEBUG", "Failed to persist log line: ${e.message}")
    }
}
