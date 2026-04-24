package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class DocxReader {
    actual suspend fun extractText(path: String): String = withContext(Dispatchers.IO) {
        try {
            val zipFile = ZipFile(File(path))
            val entry = zipFile.getEntry("word/document.xml") 
                ?: throw Exception("Not a valid DOCX file: word/document.xml missing")
            
            val content = zipFile.getInputStream(entry).bufferedReader().use { it.readText() }
            zipFile.close()
            
            // Basic XML text extraction:
            // 1. Replace <w:p> (paragraph) with newlines to preserve structure
            // 2. Strip all other tags
            val text = content
                .replace(Regex("<w:p[\\s\\S]*?>"), "\n")
                .replace(Regex("<.*?>"), "")
                .replace(Regex("\\s+"), " ") // Normalize spaces
                .trim()
            
            text
        } catch (e: Exception) {
            "Error extracting text from DOCX: ${e.message}"
        }
    }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
