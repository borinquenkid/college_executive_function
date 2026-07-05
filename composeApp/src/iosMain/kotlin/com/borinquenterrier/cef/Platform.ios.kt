package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.borinquenterrier.cef.db.DriverFactory
import okio.Path.Companion.toPath
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.experimental.ExperimentalNativeApi

actual val isDesktop: Boolean = false

@OptIn(ExperimentalNativeApi::class)
actual val isDebug: Boolean = Platform.isDebugBinary

actual val platformName: String = "iOS"

private fun documentsDirectoryUrl(): NSURL? =
    NSFileManager.defaultManager
        .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        .firstOrNull() as? NSURL

@Composable
actual fun rememberModelDirectoryPath(): String {
    return remember {
        documentsDirectoryUrl()?.path ?: ""
    }
}

@Composable
actual fun rememberDriverFactory(): DriverFactory {
    return remember { DriverFactory() }
}

// Caps the debug log file so it can't grow unbounded across a long-running session.
// The cap/trim logic itself lives in commonMain's LogFileCap.kt, shared with Android and desktop.
private const val LOG_FILE_NAME = "cef_debug_log.txt"

/**
 * Persists debug log lines to a file under the app's Documents directory, retrievable via
 * Xcode's Devices-and-Simulators container browser without needing a live debugger session —
 * useful for diagnosing anything found on a shipped/TestFlight build. Previously this was a
 * println-only stub despite the name (same gap exists on Android; out of scope here since
 * this pass is iOS-exclusive).
 *
 * Uses Okio's FileSystem (the same one PlatformFileSystem.ios.kt and IcsExport.ios.kt already
 * write through) rather than raw NSFileHandle/NSData, so there's one proven file-I/O path for
 * this whole module instead of a second, less-exercised one. Rewrites the whole file per call
 * rather than appending in place — simpler, and debug logging isn't a hot path.
 */
actual fun writeLogToFile(message: String) {
    if (!isDebug) return
    println("iOS DEBUG FILE LOG: $message")

    try {
        val documentsPath = documentsDirectoryUrl()?.path ?: return
        val path = "$documentsPath/$LOG_FILE_NAME".toPath()
        val fileSystem = getFileSystem()

        val existing = if (fileSystem.exists(path)) fileSystem.read(path) { readUtf8() } else ""
        val updated = existing + "[${NSDate()}] $message\n"
        val trimmed = capLogContent(updated)

        fileSystem.write(path, mustCreate = false) { writeUtf8(trimmed) }
    } catch (e: Exception) {
        println("[writeLogToFile] Failed to persist log line: ${e.message}")
    }
}
