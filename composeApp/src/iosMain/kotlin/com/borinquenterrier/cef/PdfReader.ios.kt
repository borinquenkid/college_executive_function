package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class PdfReader {
    actual suspend fun readSource(path: String): List<SourcePart> {
        return listOf(SourcePart("PDF extraction not yet implemented on iOS"))
    }
}

@Composable
actual fun rememberPdfReader(): PdfReader {
    return remember { PdfReader() }
}
