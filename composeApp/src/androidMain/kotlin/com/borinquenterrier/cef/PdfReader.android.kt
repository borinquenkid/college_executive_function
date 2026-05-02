package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PdfReader {
    actual suspend fun readSource(path: String): List<SourcePart> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val document = PDDocument.load(file)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            
            SourceProcessor.process(text.trim(), SourceType.TEXT)
        } catch (e: Exception) {
            listOf(SourcePart(text = "Error extracting text from PDF: ${e.message}", pageNumber = 0, type = SourceType.TEXT))
        }
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
