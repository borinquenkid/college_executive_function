package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class PdfReader() {
    suspend fun extractText(path: String): String
}

@Composable
expect fun rememberPdfReader(): PdfReader
