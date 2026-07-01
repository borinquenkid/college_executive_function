package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class DocxReader {
    suspend fun readSource(path: String): List<SourceFragment>
    suspend fun readSource(bytes: ByteArray): List<SourceFragment>
}

@Composable
expect fun rememberDocxReader(): DocxReader
