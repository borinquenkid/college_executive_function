package com.borinquenterrier.cef

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PdfReader(private val context: Context) {
    actual suspend fun readSource(path: String): List<SourceFragment> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val fileToRead = if (path.startsWith("content://")) {
                tempFile = File.createTempFile("cef_temp", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
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

            val document = PDDocument.load(fileToRead)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            
            SourceProcessor.process(text.trim(), SourceType.TEXT)
        } catch (e: Exception) {
            listOf(SourceFragment(text = "Error extracting text from PDF: ${e.message}", pageNumber = 0, type = SourceType.TEXT))
        } finally {
            tempFile?.delete()
        }
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    val context = LocalContext.current
    return remember(context) { PdfReader(context) }
}
