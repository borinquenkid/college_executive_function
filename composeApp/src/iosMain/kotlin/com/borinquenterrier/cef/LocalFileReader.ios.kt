package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

actual class LocalFileReader {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.Default) {
        try {
            val url = NSURL(string = path) ?: return@withContext "Invalid file path: $path"

            // startAccessingSecurityScopedResource is required for some iOS URLs (like those from iCloud)
            val success = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url)
                    ?: return@withContext "Could not read data from $path"
                val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)
                text?.toString() ?: "Could not decode text from $path"
            } finally {
                if (success) url.stopAccessingSecurityScopedResource()
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.Default) {
        try {
            val url = NSURL(string = path) ?: return@withContext ByteArray(0)
            val success = url.startAccessingSecurityScopedResource()
            try {
                (NSData.dataWithContentsOfURL(url) ?: return@withContext ByteArray(0)).toByteArray()
            } finally {
                if (success) url.stopAccessingSecurityScopedResource()
            }
        } catch (e: Exception) {
            // Matches readText()'s failure contract: never throw, fail to an empty/safe result
            // instead of crashing the caller (e.g. IngestionAgent.readBytes -> normalizer.normalize).
            ByteArray(0)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun listFiles(dirPath: String): List<String> = withContext(Dispatchers.Default) {
        try {
            val fileManager = NSFileManager.defaultManager
            val contents = fileManager.contentsOfDirectoryAtPath(dirPath, error = null)
            contents?.map { "$dirPath/$it" } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val src = this
    return ByteArray(len).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), src.bytes, src.length) }
    }
}

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    return remember { LocalFileReader() }
}
