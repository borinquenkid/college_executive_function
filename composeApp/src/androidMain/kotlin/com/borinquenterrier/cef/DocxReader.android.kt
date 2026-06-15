package com.borinquenterrier.cef

import android.content.Context
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

actual class DocxReader(private val context: Context, private val logger: Logger? = null) {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val fileToRead = if (path.startsWith("content://")) {
                    tempFile = File.createTempFile("cef_temp", ".docx", context.cacheDir)
                    context.contentResolver.openInputStream(path.toUri())?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                } else {
                    File(path)
                }

                if (fileToRead == null || !fileToRead.exists()) {
                    throw Exception("Could not access file at $path")
                }

                val zipFile = ZipFile(fileToRead)
                val entry = zipFile.getEntry("word/document.xml")
                    ?: throw Exception("Not a valid DOCX file: word/document.xml missing")

                val content = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
                zipFile.close()

                val text = content
                    .replace(Regex("<w:p[\\s\\S]*?>"), "\n")
                    .replace(Regex("<.*?>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                SourceProcessor.process(text, SourceType.TEXT)
            } catch (e: Exception) {
                logger?.e("DocxReader", "Failed to read DOCX at $path", e)
                listOf(
                    SourceFragment(
                        text = "Error extracting text from DOCX: ${e.message}",
                        pageNumber = 0,
                        type = SourceType.TEXT
                    )
                )
            } finally {
                tempFile?.delete()
            }
        }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    val context = LocalContext.current
    val logger = rememberLogger()
    return remember(context, logger) { DocxReader(context, logger) }
}
