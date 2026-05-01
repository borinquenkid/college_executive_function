package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class DocxReader() {
    suspend fun extractChunks(path: String): List<SourceChunk>
}

@Composable
expect fun rememberDocxReader(): DocxReader
