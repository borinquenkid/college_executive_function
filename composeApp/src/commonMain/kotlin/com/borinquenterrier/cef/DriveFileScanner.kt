package com.borinquenterrier.cef

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Scans Google Drive folders concurrently and deduplicates results against existing URIs.
 * Builds appropriate Drive queries and handles auth availability.
 * Returns only files not already ingested.
 */
class DriveFileScanner(
    private val driveService: GoogleDriveService,
    private val tokenRepository: GoogleTokenRepository,
    private val directoryPreferences: DirectoryPreferencesManager,
    private val logger: Logger
) {
    private val tag = "DriveFileScanner"

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

        val newFiles = mutableListOf<DriveFile>()
        coroutineScope {
            val deferreds = watchedFolders.map { folderId ->
                async {
                    try {
                        val query = buildDriveQuery(folderId)
                        driveService.listFiles(query)
                    } catch (e: Exception) {
                        logger.e(tag, "Failed to list files for drive folder: $folderId", e)
                        emptyList()
                    }
                }
            }
            for (files in deferreds.map { it.await() }) {
                for (file in files) {
                    val uri = "google_drive://${file.id}"
                    if (!existingUris.contains(uri) && newFiles.none { it.id == file.id }) {
                        newFiles.add(file)
                    }
                }
            }
        }
        logger.d(tag, "Found ${newFiles.size} new GDrive files from ${watchedFolders.size} folders")
        return newFiles
    }

    private fun buildDriveQuery(folderId: String): String {
        return "'$folderId' in parents and (" +
            "mimeType = 'application/vnd.google-apps.document' " +
            "or mimeType = 'application/pdf' " +
            "or mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' " +
            "or mimeType = 'text/plain' " +
            "or name contains '.ics')"
    }
}
