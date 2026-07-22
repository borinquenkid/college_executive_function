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
import platform.posix.memcpy

actual class DocxReader {
    @OptIn(kotlinx.cinterop.BetaInteropApi::class)
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.Default) {
            try {
                val url = NSURL(string = path) ?: return@withContext docxError("Invalid DOCX path")
                val data = NSData.create(contentsOfURL = url) ?: return@withContext docxError("Could not read DOCX file")
                extractFragments(data.toByteArray())
            } catch (e: Exception) {
                println("[DocxReader.ios] Failed to read DOCX from path $path: ${e.message}")
                docxError(e.message ?: "Unknown error")
            }
        }

    actual suspend fun readSource(bytes: ByteArray): List<SourceFragment> =
        withContext(Dispatchers.Default) {
            try {
                extractFragments(bytes)
            } catch (e: Exception) {
                println("[DocxReader.ios] Failed to read DOCX from bytes: ${e.message}")
                docxError(e.message ?: "Unknown error")
            }
        }

    private fun extractFragments(archive: ByteArray): List<SourceFragment> {
        val text = DocxContentExtractor.extractPlainText(archive)
            ?: return docxError("Not a valid DOCX file: word/document.xml missing")
        return SourceProcessor.process(text, SourceType.TEXT)
    }

    private fun docxError(message: String) = listOf(
        SourceFragment(text = "Error extracting text from DOCX: $message", pageNumber = 0, type = SourceType.TEXT)
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
