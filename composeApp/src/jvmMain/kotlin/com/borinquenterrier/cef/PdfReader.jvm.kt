package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PdfReader {
    actual suspend fun readSource(path: String): List<SourceFragment> = withContext(Dispatchers.IO) {
        val parts = mutableListOf<SourceFragment>()
        try {
            val file = File(path)
            val document = Loader.loadPDF(file)
            val stripper = PDFTextStripper()

            for (i in 1..document.numberOfPages) {
                stripper.startPage = i
                stripper.endPage = i
                val text = stripper.getText(document).trim()
                if (text.isNotEmpty()) {
                    parts.add(SourceFragment(
                        text = text,
                        pageNumber = i,
                        type = SourceType.TEXT
                    ))
                }
            }
            document.close()
        } catch (e: Exception) {
            parts.add(SourceFragment(text = "Error: ${e.message}", type = SourceType.TEXT))
        }
        parts
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
