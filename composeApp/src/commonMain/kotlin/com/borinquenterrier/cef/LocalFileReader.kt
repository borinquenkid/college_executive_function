package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class LocalFileReader() {
    suspend fun readText(path: String): String
}

@Composable
expect fun rememberLocalFileReader(): LocalFileReader
