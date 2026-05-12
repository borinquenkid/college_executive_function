package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument

actual class PdfReader {
    actual suspend fun readSource(path: String): List<SourceFragment> = withContext(Dispatchers.Default) {
        try {
            val url = NSURL(string = path) ?: return@withContext listOf(SourceFragment("Invalid PDF path"))
            val document = PDFDocument(uRL = url)
            val text = document.string ?: ""
            
            SourceProcessor.process(text, SourceType.TEXT)
        } catch (e: Exception) {
            listOf(SourceFragment("Error extracting PDF text: ${e.message}"))
        }
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
