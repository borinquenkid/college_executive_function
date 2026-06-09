package com.borinquenterrier.cef

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fetches files from multiple local directories concurrently.
 * Handles error recovery per directory and result aggregation.
 */
class LocalFileFetcher(
    private val fileReader: LocalFileReader,
    private val logger: Logger?
) {
    private val tag = "LocalFileFetcher"

    suspend fun fetchFromDirectories(dirPaths: List<String>): List<String> {
        val allFiles = mutableListOf<String>()

        coroutineScope {
            val deferreds = dirPaths.map { dir ->
                async {
                    try {
                        fileReader.listFiles(dir)
                    } catch (e: Exception) {
                        logger?.e(tag, "Failed to list local files in directory: $dir", e)
                        emptyList()
                    }
                }
            }

            for (files in deferreds.map { it.await() }) {
                allFiles.addAll(files)
            }
        }

        return allFiles
    }
}
