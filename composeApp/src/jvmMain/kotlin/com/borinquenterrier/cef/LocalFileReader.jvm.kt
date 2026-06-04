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

    actual suspend fun listFiles(dirPath: String): List<String> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
        } else {
            emptyList()
        }
    }
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    return remember { LocalFileReader() }
}
