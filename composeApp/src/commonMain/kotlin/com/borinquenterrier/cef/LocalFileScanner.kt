package com.borinquenterrier.cef

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Scans local directories concurrently and deduplicates results against existing URIs.
 * Returns only files not already ingested.
 */
class LocalFileScanner(
    private val fileReader: LocalFileReader,
    private val directoryPreferences: DirectoryPreferencesManager,
    private val logger: Logger
) {
    private val tag = "LocalFileScanner"

    suspend fun scanNewFiles(existingUris: Set<String>): List<String> {
        val watchedDirs = directoryPreferences.getWatchedLocalDirectories()
        if (watchedDirs.isEmpty()) {
            logger.d(tag, "No local directories configured for scanning")
            return emptyList()
        }

        val newFiles = mutableListOf<String>()
        coroutineScope {
            val deferreds = watchedDirs.map { dir ->
                async {
                    try {
                        fileReader.listFiles(dir)
                    } catch (e: Exception) {
                        logger.e(tag, "Failed to list local files in directory: $dir", e)
                        emptyList()
                    }
                }
            }
            for (files in deferreds.map { it.await() }) {
                for (file in files) {
                    if (!existingUris.contains(file) && !newFiles.contains(file)) {
                        newFiles.add(file)
                    }
                }
            }
        }
        logger.d(tag, "Found ${newFiles.size} new local files from ${watchedDirs.size} directories")
        return newFiles
    }
}
