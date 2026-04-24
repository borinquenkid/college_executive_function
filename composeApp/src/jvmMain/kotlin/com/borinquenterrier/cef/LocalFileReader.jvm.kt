package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class LocalFileReader {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText()
    }
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    return remember { LocalFileReader() }
}
