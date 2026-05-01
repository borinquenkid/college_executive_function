package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.borinquenterrier.cef.db.DriverFactory

actual val isDesktop: Boolean = true
actual val isDebug: Boolean = System.getProperty("debug") != "false" && System.getenv("DEBUG") != "false"

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
    } catch (e: Exception) {
        // Fallback to console if file writing fails
    }
}
