package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class PdfReader() {
    suspend fun extractChunks(path: String): List<SourceChunk>
}

@Composable
expect fun rememberPdfReader(): PdfReader
