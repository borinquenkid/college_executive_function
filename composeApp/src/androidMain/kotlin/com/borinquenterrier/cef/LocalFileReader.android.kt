package com.borinquenterrier.cef

import android.content.Context
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class LocalFileReader(private val context: Context, private val logger: Logger? = null) {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(path.toUri())?.use { input ->
                    input.bufferedReader().use { it.readText() }
                } ?: "Error: Could not open content URI"
            } else {
                File(path).readText()
            }
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
    val context = LocalContext.current
    val logger = rememberLogger()
    return remember(context, logger) { LocalFileReader(context, logger) }
}
