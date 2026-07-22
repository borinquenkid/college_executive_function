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
import java.util.zip.ZipInputStream

actual class DocxReader(private val context: Context) {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val fileToRead = if (path.startsWith("content://")) {
                    tempFile = File.createTempFile("cef_temp", ".docx", context.cacheDir)
                    context.contentResolver.openInputStream(path.toUri())?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile
                } else {
                    File(path)
                }
                if (fileToRead == null || !fileToRead.exists()) {
                    throw Exception("Could not access file at $path")
                }
                val content = ZipFile(fileToRead).use { zip ->
                    val entry = zip.getEntry("word/document.xml")
                        ?: throw Exception("Not a valid DOCX file: word/document.xml missing")
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
                fromDocumentXml(content)
            } catch (e: Exception) {
                println("[DocxReader] Failed to read DOCX source at $path: ${e.message}")
                docxError(e)
            } finally {
                tempFile?.delete()
            }
        }

    actual suspend fun readSource(bytes: ByteArray): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            try {
                val content = ZipInputStream(bytes.inputStream()).use { zis ->
                    generateSequence { zis.nextEntry }.firstOrNull { it.name == "word/document.xml" }
                        ?: throw Exception("Not a valid DOCX file: word/document.xml missing")
                    zis.bufferedReader().readText()
                }
                fromDocumentXml(content)
            } catch (e: Exception) {
                println("[DocxReader] Failed to read DOCX source from bytes: ${e.message}")
                docxError(e)
            }
        }

    private fun fromDocumentXml(content: String): List<SourceFragment> {
        val text = content
            .replace(Regex("<w:p[\\s\\S]*?>"), "\n")
            .replace(Regex("<.*?>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return SourceProcessor.process(text, SourceType.TEXT)
    }

    private fun docxError(e: Exception) = listOf(
        SourceFragment(text = "Error extracting text from DOCX: ${e.message}", pageNumber = 0, type = SourceType.TEXT)
    )
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    val context = LocalContext.current
    return remember(context) { DocxReader(context) }
}
