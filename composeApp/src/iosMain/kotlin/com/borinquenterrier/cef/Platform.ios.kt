package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.borinquenterrier.cef.db.DriverFactory
import kotlin.native.Platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlin.experimental.ExperimentalNativeApi

actual val isDesktop: Boolean = false

@OptIn(ExperimentalNativeApi::class)
actual val isDebug: Boolean = Platform.isDebugBinary

actual val platformName: String = "iOS"

@Composable
actual fun rememberModelDirectoryPath(): String {
    return remember {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentsDirectory = paths.first() as platform.Foundation.NSURL
        documentsDirectory.path ?: ""
    }
}

@Composable
actual fun rememberDriverFactory(): DriverFactory {
    return remember { DriverFactory() }
}

actual fun writeLogToFile(message: String) {
    if (!isDebug) return
    // On iOS, we normally use NSLog or specialized file writers.
    // For now, simple print is usually sufficient for debugging.
    println("iOS DEBUG FILE LOG: $message")
}
