package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

actual class DocxReader {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            val parts = mutableListOf<SourceFragment>()
            try {
                val zipFile = ZipFile(File(path))
                val entry = zipFile.getEntry("word/document.xml")
                    ?: throw Exception("Not a valid DOCX file: word/document.xml missing")

                val content = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
                zipFile.close()

                // Split by paragraph tag to create parts
                val paragraphs = content.split(Regex("(?=<w:p[\\s\\S]*?>)"))

                for (p in paragraphs) {
                    val text = p.replace(Regex("<.*?>"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (text.isNotEmpty()) {
                        parts.add(SourceFragment(text = text, type = SourceType.TEXT))
                    }
                }
            } catch (e: Exception) {
                parts.add(SourceFragment(text = "Error: ${e.message}", type = SourceType.TEXT))
            }
            parts
        }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
