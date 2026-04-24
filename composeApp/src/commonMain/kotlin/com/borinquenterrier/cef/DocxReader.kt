package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class DocxReader() {
    suspend fun extractText(path: String): String
}

@Composable
expect fun rememberDocxReader(): DocxReader
