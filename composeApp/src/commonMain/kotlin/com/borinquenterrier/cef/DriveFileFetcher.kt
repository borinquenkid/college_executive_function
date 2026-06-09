package com.borinquenterrier.cef

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fetches files from multiple Google Drive folders concurrently.
 * Handles error recovery per folder and result collection.
 */
class DriveFileFetcher(
    private val driveService: GoogleDriveService,
    private val queryBuilder: DriveQueryBuilder,
    private val logger: Logger?
) {
    private val tag = "DriveFileFetcher"

    suspend fun fetchFromFolders(folderIds: List<String>): List<DriveFile> {
        val allFiles = mutableListOf<DriveFile>()

        coroutineScope {
            val deferreds = folderIds.map { folderId ->
                async {
                    try {
                        val query = queryBuilder.buildQueryForFolder(folderId)
                        driveService.listFiles(query)
                    } catch (e: Exception) {
                        logger?.e(tag, "Failed to list files for drive folder: $folderId", e)
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

    fun deduplicateFiles(files: List<DriveFile>, existingUris: Set<String>): List<DriveFile> {
        val seenIds = mutableSetOf<String>()
        return files.filter { file ->
            val uri = "google_drive://${file.id}"
            val isNew = !existingUris.contains(uri) && seenIds.add(file.id)
            isNew
        }
    }
}
