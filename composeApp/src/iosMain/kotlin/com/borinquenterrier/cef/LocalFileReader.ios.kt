package com.borinquenterrier.cef

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.NSFileManager
import kotlinx.cinterop.ExperimentalForeignApi

actual class LocalFileReader {
    actual suspend fun readText(path: String): String = withContext(Dispatchers.Default) {
        try {
            val url = NSURL(string = path) ?: return@withContext "Invalid file path: $path"
            
            // startAccessingSecurityScopedResource is required for some iOS URLs (like those from iCloud)
            val success = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url) ?: return@withContext "Could not read data from $path"
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

@Composable
actual fun rememberLocalFileReader(): LocalFileReader {
    return remember { LocalFileReader() }
}
