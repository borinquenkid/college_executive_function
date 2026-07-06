package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class LocalFileReader {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText()
    }

    actual suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    actual suspend fun listFiles(dirPath: String): List<String> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
        } else {
            emptyList()
        }
    }

    // Desktop's file picker always hands back a plain local path, which already carries its own
    // name — callers recover it by parsing the path.
    actual suspend fun resolveDisplayName(path: String): String = ""
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    return remember { LocalFileReader() }
}
