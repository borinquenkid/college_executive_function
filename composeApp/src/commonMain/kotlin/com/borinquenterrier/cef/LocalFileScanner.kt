package com.borinquenterrier.cef

/**
 * Lightweight facade orchestrating local file scanning.
 * Delegates to specialized services:
 * - LocalFileFetcher: Concurrent directory scanning
 * - LocalFileFilter: Deduplication and filtering
 */
class LocalFileScanner(
    private val fileReader: LocalFileReader,
    private val directoryPreferences: DirectoryPreferencesManager,
    private val logger: Logger
) {
    private val tag = "LocalFileScanner"
    private val fetcher = LocalFileFetcher(fileReader, logger)
    private val filter = LocalFileFilter()

    suspend fun scanNewFiles(existingUris: Set<String>): List<String> {
        val watchedDirs = directoryPreferences.getWatchedLocalDirectories()
        if (watchedDirs.isEmpty()) {
            logger.d(tag, "No local directories configured for scanning")
            return emptyList()
        }

        val allFiles = fetcher.fetchFromDirectories(watchedDirs)
        val newFiles = filter.filterNewFiles(allFiles, existingUris)

        logger.d(tag, "Found ${newFiles.size} new local files from ${watchedDirs.size} directories")
        return newFiles
    }
}
