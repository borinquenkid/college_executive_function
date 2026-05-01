package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PdfReader {
    actual suspend fun extractText(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val document = PDDocument.load(file)
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
