package com.borinquenterrier.cef

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class LocalFileReader(private val context: Context) {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
                input.bufferedReader().use { it.readText() }
            } ?: "Error: Could not open content URI"
        } else {
            File(path).readText()
        }
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
    val context = LocalContext.current
    return remember(context) { LocalFileReader(context) }
}
