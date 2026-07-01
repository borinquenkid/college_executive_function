package com.borinquenterrier.cef

import android.content.Context
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class PdfReader(private val context: Context) {
    actual suspend fun readSource(path: String): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val fileToRead = if (path.startsWith("content://")) {
                    tempFile = File.createTempFile("cef_temp", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(path.toUri())?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                } else {
                    File(path)
                }

                if (fileToRead == null || !fileToRead.exists()) {
                    throw Exception("Could not access file at $path")
                }

                PDFBoxResourceLoader.init(context)
                extract(PDDocument.load(fileToRead))
            } catch (e: Exception) {
                listOf(
                    SourceFragment(
                        text = "Error extracting text from PDF: ${e.message}",
                        pageNumber = 0,
                        type = SourceType.TEXT
                    )
                )
            } finally {
                tempFile?.delete()
            }
        }

    actual suspend fun readSource(bytes: ByteArray): List<SourceFragment> =
        withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context)
                extract(PDDocument.load(bytes))
            } catch (e: Exception) {
                listOf(SourceFragment(text = "Error extracting text from PDF: ${e.message}", pageNumber = 0, type = SourceType.TEXT))
            }
        }

    private fun extract(document: PDDocument): List<SourceFragment> {
        val stripper = PDFTextStripper()
        val fragments = mutableListOf<SourceFragment>()
        document.use { doc ->
            for (i in 0 until doc.numberOfPages) {
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val text = stripper.getText(doc).trim()
                if (text.isNotEmpty()) {
                    fragments.add(
                        SourceFragment(text = text, pageNumber = i + 1, type = SourceType.TEXT, sectionTitle = "Page ${i + 1}")
                    )
                }
            }
        }
        return fragments.ifEmpty {
            listOf(SourceFragment(text = "No text content found in PDF", pageNumber = 0, type = SourceType.TEXT))
        }
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    val context = LocalContext.current
    return remember(context) { PdfReader(context) }
}
