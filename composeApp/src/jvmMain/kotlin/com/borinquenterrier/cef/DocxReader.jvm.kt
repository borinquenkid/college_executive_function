package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

actual class DocxReader {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            fromDocumentXml {
                ZipFile(File(path)).use { zip ->
                    val entry = zip.getEntry("word/document.xml")
                        ?: throw Exception("Not a valid DOCX file: word/document.xml missing")
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
            }
        }

    actual suspend fun readSource(bytes: ByteArray): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            fromDocumentXml {
                ZipInputStream(bytes.inputStream()).use { zis ->
                    generateSequence { zis.nextEntry }.firstOrNull { it.name == "word/document.xml" }
                        ?: throw Exception("Not a valid DOCX file: word/document.xml missing")
                    zis.bufferedReader().readText()
                }
            }
        }

    private inline fun fromDocumentXml(readXml: () -> String): List<SourceFragment> {
        val parts = mutableListOf<SourceFragment>()
        try {
            val content = readXml()
            for (p in content.split(Regex("(?=<w:p[\\s\\S]*?>)"))) {
                val text = p.replace(Regex("<.*?>"), "").replace(Regex("\\s+"), " ").trim()
                if (text.isNotEmpty()) parts.add(SourceFragment(text = text, type = SourceType.TEXT))
            }
        } catch (e: Exception) {
            parts.add(SourceFragment(text = "Error: ${e.message}", type = SourceType.TEXT))
        }
        return parts
    }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
