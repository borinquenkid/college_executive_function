package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class LocalFileReader(private val logger: Logger? = null) {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        try {
            File(path).readText()
        } catch (e: Exception) {
            logger?.e("LocalFileReader", "Failed to read file at $path", e)
            "Error reading file: ${e.message}"
        }
    }

    actual suspend fun listFiles(dirPath: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger?.e("LocalFileReader", "Failed to list files in $dirPath", e)
            emptyList()
        }
    }
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    val logger = rememberLogger()
    return remember(logger) { LocalFileReader(logger) }
}
