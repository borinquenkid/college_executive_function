package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class PdfReader {
    suspend fun readSource(path: String): List<SourceFragment>
}

@Composable
expect fun rememberPdfReader(): PdfReader
