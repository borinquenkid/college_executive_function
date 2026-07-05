package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.borinquenterrier.cef.db.DriverFactory

actual val isDesktop: Boolean = true
actual val isDebug: Boolean = computeIsDebug(
    isPackagedRelease = IS_PACKAGED_DESKTOP_RELEASE,
    debugSystemProperty = System.getProperty("debug"),
    debugEnvVar = System.getenv("DEBUG"),
)
actual val platformName: String = "JVM/Desktop"

@Composable
actual fun rememberModelDirectoryPath(): String {
    return remember {
        val userDir = System.getProperty("user.dir") ?: "."
        val root = if (userDir.endsWith("composeApp")) {
            java.io.File(userDir).parentFile ?: java.io.File(userDir)
        } else {
            java.io.File(userDir)
        }
        java.io.File(root, "models").apply { mkdirs() }.absolutePath
    }
}

@Composable
actual fun rememberDriverFactory(): DriverFactory {
    return remember { DriverFactory() }
}

// Caps the debug log file so it can't grow unbounded across a long-running session — Android and
// iOS already did this (HARD-6, ROADMAP.md Phase 10); desktop's debug_logs.txt had grown to 36MB
// with no cap before this fix. Trim logic itself lives in commonMain's LogFileCap.kt.
internal fun capLogFile(file: java.io.File, maxBytes: Int = MAX_LOG_FILE_BYTES) {
    if (file.length() > maxBytes) {
        file.writeText(capLogContent(file.readText(), maxBytes))
    }
}

actual fun writeLogToFile(message: String) {
    if (!isDebug) return
    try {
        // Resolve the root directory relative to the current working directory
        val userDir = System.getProperty("user.dir") ?: "."
        val root = if (userDir.endsWith("composeApp")) {
            java.io.File(userDir).parentFile ?: java.io.File(userDir)
        } else {
            java.io.File(userDir)
        }

        val file = java.io.File(root, "debug_logs.txt")
        file.appendText("${java.time.LocalDateTime.now()} $message\n")
        capLogFile(file)
    } catch (e: Exception) {
        // Fallback to console if file writing fails
    }
}
