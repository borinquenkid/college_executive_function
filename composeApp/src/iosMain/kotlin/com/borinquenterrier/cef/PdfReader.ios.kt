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
            val document = PDFDocument(uRL = url) ?: return@withContext listOf(SourceFragment("Could not load PDF document"))
            
            val fragments = mutableListOf<SourceFragment>()
            for (i in 0 until document.pageCount.toInt()) {
                val page = document.pageAtIndex(i.toULong())
                val text = page?.string ?: ""
                if (text.isNotBlank()) {
                    fragments.add(
                        SourceFragment(
                            text = text,
                            pageNumber = i + 1,
                            type = SourceType.TEXT,
                            sectionTitle = "Page ${i + 1}"
                        )
                    )
                }
            }
            
            if (fragments.isEmpty()) {
                listOf(SourceFragment("No text content found in PDF"))
            } else {
                fragments
            }
        } catch (e: Exception) {
            listOf(SourceFragment("Error extracting PDF text: ${e.message}"))
        }
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
