package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class LocalFileReader {
    actual suspend fun readText(path: String): String {
        // iOS implementation would use NSData or NSString reading from path
        return "" 
    }
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    return remember { LocalFileReader() }
}
