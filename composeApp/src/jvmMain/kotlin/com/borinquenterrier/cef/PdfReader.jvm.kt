package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PdfReader {
    actual suspend fun extractText(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val document = Loader.loadPDF(file)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            text.trim()
        } catch (e: Exception) {
            "Error extracting text from PDF: ${e.message}"
        }
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
