package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Scans for new files from both local directories and Google Drive.
 * Manages watched directory/folder configuration and deduplication logic.
 */
class SourceScanner(
    private val fileReader: LocalFileReader,
    private val driveService: GoogleDriveService,
    private val tokenRepository: GoogleTokenRepository,
    private val settings: Settings,
    private val logger: Logger
) {
    private val tag = "SourceScanner"

    fun getWatchedLocalDirectories(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "")
        if (jsonString.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setWatchedLocalDirectories(dirs: List<String>) {
        settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", Json.encodeToString(dirs))
    }

    fun getWatchedGDriveFolders(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "")
        if (jsonString.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setWatchedGDriveFolders(folders: List<String>) {
        settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", Json.encodeToString(folders))
    }

    /**
     * Scans all watched local directories concurrently and returns only files not already ingested.
     */
    suspend fun scanNewLocalFiles(existingUris: Set<String>): List<String> {
        val newFiles = mutableListOf<String>()
        coroutineScope {
            val deferreds = getWatchedLocalDirectories().map { dir ->
                async {
                    try { fileReader.listFiles(dir) }
                    catch (e: Exception) {
                        logger.e(tag, "Failed to list local files in directory: $dir", e)
                        emptyList()
                    }
                }
            }
            for (files in deferreds.map { it.await() }) {
                for (file in files) {
                    if (!existingUris.contains(file) && !newFiles.contains(file)) newFiles.add(file)
                }
            }
        }
        return newFiles
    }

    /**
     * Scans all watched GDrive folders concurrently and returns only files not already ingested.
     * Returns empty list if Google auth is not available.
     */
    suspend fun scanNewDriveFiles(existingUris: Set<String>): List<DriveFile> {
        if (!tokenRepository.hasTokens()) {
            logger.d(tag, "Skipping GDrive scanning as auth is not available/configured.")
            return emptyList()
        }
        val newFiles = mutableListOf<DriveFile>()
        coroutineScope {
            val deferreds = getWatchedGDriveFolders().map { folderId ->
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
                    if (!existingUris.contains(uri) && newFiles.none { it.id == file.id }) newFiles.add(file)
                }
            }
        }
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
