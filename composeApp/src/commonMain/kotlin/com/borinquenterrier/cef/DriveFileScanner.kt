package com.borinquenterrier.cef

/**
 * Lightweight facade orchestrating Google Drive scanning.
 * Delegates to specialized services:
 * - DriveQueryBuilder: Query construction
 * - DriveFileFetcher: Concurrent folder fetching and deduplication
 */
class DriveFileScanner(
    driveService: GoogleDriveService,
    private val tokenRepository: GoogleTokenRepository,
    private val directoryPreferences: DirectoryPreferencesManager,
    private val logger: Logger,
) {
    private val tag = "DriveFileScanner"
    private val queryBuilder = DriveQueryBuilder()
    private val folderFetcher = ConcurrentFolderFetcher(driveService, queryBuilder, logger)
    private val duplicateFilter = FileDuplicateFilter()
    private val fileFetcher = DriveFileFetcher(folderFetcher, duplicateFilter)

    suspend fun scanNewFiles(existingUris: Set<String>): List<DriveFile> {
        if (!tokenRepository.hasTokens()) {
            logger.d(tag, "Skipping GDrive scanning as auth is not available/configured")
            return emptyList()
        }

        val watchedFolders = directoryPreferences.getWatchedGDriveFolders()
        if (watchedFolders.isEmpty()) {
            logger.d(tag, "No GDrive folders configured for scanning")
            return emptyList()
        }

        val newFiles = fileFetcher.fetchFromFolders(watchedFolders, existingUris)

        logger.d(tag, "Found ${newFiles.size} new GDrive files from ${watchedFolders.size} folders")
        return newFiles
    }
}

