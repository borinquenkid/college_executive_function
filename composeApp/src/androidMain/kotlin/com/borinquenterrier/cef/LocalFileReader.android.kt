package com.borinquenterrier.cef

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class LocalFileReader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    actual suspend fun readText(path: String): String = withContext(ioDispatcher) {
        try {
            if (path.startsWith(CONTENT_SCHEME)) {
                context.contentResolver.openInputStream(path.toUri())?.use { input ->
                    input.bufferedReader().use { it.readText() }
                } ?: "Error: Could not open content URI"
            } else {
                File(path).readText()
            }
        } catch (e: Exception) {
            println("[LocalFileReader] Failed to read text from $path: ${e.message}")
            "Error reading file: ${e.message}"
        }
    }

    // Matches readText()'s never-throw contract: a revoked/invalid content:// URI throws
    // SecurityException/FileNotFoundException, a missing local path throws IOException —
    // this had no try/catch at all before, unlike its sibling above.
    actual suspend fun readBytes(path: String): ByteArray = withContext(ioDispatcher) {
        try {
            if (path.startsWith(CONTENT_SCHEME)) {
                context.contentResolver.openInputStream(path.toUri())?.use { it.readBytes() } ?: ByteArray(0)
            } else {
                File(path).readBytes()
            }
        } catch (e: Exception) {
            println("[LocalFileReader] Failed to read bytes from $path: ${e.message}")
            ByteArray(0)
        }
    }

    actual suspend fun listFiles(dirPath: String): List<String> = withContext(ioDispatcher) {
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
        } else {
            emptyList()
        }
    }

    // Cloud-storage document providers (Google Drive, Dropbox, etc.) hand back an opaque
    // content:// URI whose path segment carries no filename/extension — unlike on-device
    // storage providers, whose URI happens to embed the real path. DISPLAY_NAME is the only
    // reliable way to recover the real name across every SAF provider.
    actual suspend fun resolveDisplayName(path: String): String = withContext(ioDispatcher) {
        if (!path.startsWith(CONTENT_SCHEME)) return@withContext ""
        try {
            context.contentResolver.query(path.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                } ?: ""
        } catch (e: Exception) {
            println("[LocalFileReader] Failed to resolve display name for $path: ${e.message}")
            ""
        }
    }

    private companion object {
        const val CONTENT_SCHEME = "content://"
    }
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    val context = LocalContext.current
    return remember(context) { LocalFileReader(context) }
}
