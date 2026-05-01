package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class DocxReader {
    actual suspend fun extractChunks(path: String): List<SourceChunk> = withContext(Dispatchers.IO) {
        val chunks = mutableListOf<SourceChunk>()
        try {
            val zipFile = ZipFile(File(path))
            val entry = zipFile.getEntry("word/document.xml") 
                ?: throw Exception("Not a valid DOCX file: word/document.xml missing")
            
            val content = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
            zipFile.close()
            
            // Split by paragraph tag to create chunks
            val paragraphs = content.split(Regex("(?=<w:p[\\s\\S]*?>)"))
            
            for (p in paragraphs) {
                val text = p.replace(Regex("<.*?>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (text.isNotEmpty()) {
                    chunks.add(SourceChunk(text = text, type = SourceType.TEXT))
                }
            }
        } catch (e: Exception) {
            chunks.add(SourceChunk(text = "Error: ${e.message}"))
        }
        chunks
    }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
