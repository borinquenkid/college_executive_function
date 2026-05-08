package com.borinquenterrier.cef

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    val context = LocalContext.current
    return remember(context) { LocalFileReader(context) }
}
