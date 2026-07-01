package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.PDFKit.PDFDocument

actual class PdfReader {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.Default) {
            val url = NSURL(string = path) ?: return@withContext listOf(SourceFragment("Invalid PDF path"))
            extract(PDFDocument(uRL = url))
        }

    actual suspend fun readSource(bytes: ByteArray): List<SourceFragment> =
        withContext(Dispatchers.Default) {
            extract(PDFDocument(data = bytes.toNSData()))
        }

    private fun extract(document: PDFDocument?): List<SourceFragment> {
        return try {
            if (document == null) return listOf(SourceFragment("Could not load PDF document"))
            val fragments = mutableListOf<SourceFragment>()
            for (i in 0 until document.pageCount.toInt()) {
                val text = document.pageAtIndex(i.toULong())?.string ?: ""
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
            if (fragments.isEmpty()) listOf(SourceFragment("No text content found in PDF")) else fragments
        } catch (e: Exception) {
            listOf(SourceFragment("Error extracting PDF text: ${e.message}"))
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
