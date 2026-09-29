package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

actual class PdfReader {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.IO) { extract { Loader.loadPDF(File(path)) } }

    actual suspend fun readSource(bytes: ByteArray): List<SourceFragment> =
        withContext(Dispatchers.IO) { extract { Loader.loadPDF(bytes) } }

    // One page → one fragment, shared by the path and bytes entry points.
    private fun extract(load: () -> PDDocument): List<SourceFragment> {
        val parts = mutableListOf<SourceFragment>()
        try {
            load().use { document ->
                val stripper = PDFTextStripper()
                // Position order, not content-stream order: keeps each table row on one line. In stream order a
                // schedule table came out column by column (all dates, then all topics), so the model had to guess
                // which date went with which topic — and guessed differently run to run (UT BIO 325L, 2026-09-29).
                stripper.sortByPosition = true
                for (i in 1..document.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val text = stripper.getText(document).trim()
                    if (text.isNotEmpty()) {
                        parts.add(SourceFragment(text = text, pageNumber = i, type = SourceType.TEXT))
                    }
                }
            }
        } catch (e: Exception) {
            println("[PdfReader] extract failed: ${e.message}")
            parts.add(SourceFragment(text = "Error: ${e.message}", type = SourceType.TEXT))
        }
        return parts
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
