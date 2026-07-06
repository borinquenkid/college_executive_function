package com.borinquenterrier.cef

import androidx.compose.runtime.Composable

expect class LocalFileReader {
    suspend fun readText(path: String): String
    suspend fun readBytes(path: String): ByteArray
    suspend fun listFiles(dirPath: String): List<String>

    /**
     * Resolves the real display name (with extension) for [path], when the platform can recover
     * one that the raw path string doesn't already carry — e.g. an opaque Android `content://` URI
     * from a cloud-storage document provider. Returns "" when [path] already carries its own name
     * (a plain file path) or no better name is available, so callers fall back to parsing [path].
     */
    suspend fun resolveDisplayName(path: String): String
}

@Composable
expect fun rememberLocalFileReader(): LocalFileReader
