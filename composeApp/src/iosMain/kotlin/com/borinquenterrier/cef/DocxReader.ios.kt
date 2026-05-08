package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class DocxReader {
    actual suspend fun readSource(path: String): List<SourcePart> {
        // iOS implementation would use a native ZIP library or SSZipArchive
        return listOf(SourcePart("DOCX extraction not yet implemented on iOS"))
    }
}

@Composable
actual fun rememberDocxReader(): DocxReader {
    return remember { DocxReader() }
}
