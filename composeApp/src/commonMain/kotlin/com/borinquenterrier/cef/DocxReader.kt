package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class DocxReader {
    suspend fun readSource(path: String): List<SourcePart>
}

@Composable
expect fun rememberDocxReader(): DocxReader
