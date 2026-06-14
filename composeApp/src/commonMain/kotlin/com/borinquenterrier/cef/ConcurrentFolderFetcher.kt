package com.borinquenterrier.cef

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fetches files from multiple Google Drive folders concurrently.
 * Handles per-folder error recovery while aggregating results.
 */
class ConcurrentFolderFetcher(
    private val driveService: GoogleDriveService,
    private val queryBuilder: DriveQueryBuilder,
    private val logger: Logger?
) {
    private val tag = "ConcurrentFolderFetcher"

    /**
     * Fetch files from multiple folders concurrently.
     * Failures in individual folders do not prevent fetching from other folders.
     *
     * @param folderIds List of Google Drive folder IDs to fetch from
     * @return Aggregated list of files from all folders (empty list for failed folders)
     */
    suspend fun fetchFromFolders(folderIds: List<String>): List<DriveFile> = coroutineScope {
        folderIds.map { folderId ->
            async {
                fetchFromFolder(folderId)
            }
        }.awaitAll().flatten()
    }

    private suspend fun fetchFromFolder(folderId: String): List<DriveFile> {
        return try {
            val query = queryBuilder.buildQueryForFolder(folderId)
            driveService.listFiles(query)
        } catch (e: Exception) {
            logger?.e(tag, "Failed to list files for drive folder: $folderId", e)
            emptyList()
        }
    }
}
