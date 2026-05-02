package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class DocxReader {
    actual suspend fun readSource(path: String): List<SourcePart> = withContext(Dispatchers.IO) {
        try {
            val zipFile = ZipFile(File(path))
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
            listOf(SourcePart(text = "Error extracting text from DOCX: ${e.message}", pageNumber = 0, type = SourceType.TEXT))
        }
    }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
