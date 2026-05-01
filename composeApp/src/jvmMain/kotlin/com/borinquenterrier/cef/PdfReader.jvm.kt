package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PdfReader {
    actual suspend fun extractChunks(path: String): List<SourceChunk> = withContext(Dispatchers.IO) {
        val chunks = mutableListOf<SourceChunk>()
        try {
            val file = File(path)
            val document = Loader.loadPDF(file)
            val stripper = PDFTextStripper()
            
            for (i in 1..document.numberOfPages) {
                stripper.startPage = i
                stripper.endPage = i
                val pageText = stripper.getText(document).trim()
                if (pageText.isNotEmpty()) {
                    chunks.add(SourceChunk(
                        text = pageText,
                        pageNumber = i,
                        type = SourceType.TEXT
                    ))
                }
            }
            document.close()
        } catch (e: Exception) {
            chunks.add(SourceChunk(text = "Error: ${e.message}"))
        }
        chunks
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
