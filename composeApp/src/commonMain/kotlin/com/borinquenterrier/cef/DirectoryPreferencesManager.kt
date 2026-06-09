package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * Manages persistent preferences for watched directories and Google Drive folders.
 * Handles serialization and deserialization of watched path lists.
 */
class DirectoryPreferencesManager(
    private val settings: Settings,
    private val logger: Logger
) {
    private val tag = "DirectoryPreferencesManager"

    fun getWatchedLocalDirectories(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "")
        if (jsonString.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            logger.e(tag, "Failed to deserialize watched local directories", e)
            emptyList()
        }
    }

    fun setWatchedLocalDirectories(dirs: List<String>) {
        try {
            settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", Json.encodeToString(dirs))
            logger.d(tag, "Saved ${dirs.size} watched local directories")
        } catch (e: Exception) {
            logger.e(tag, "Failed to save watched local directories", e)
        }
    }

    fun getWatchedGDriveFolders(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "")
        if (jsonString.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            logger.e(tag, "Failed to deserialize watched GDrive folders", e)
            emptyList()
        }
    }

    fun setWatchedGDriveFolders(folders: List<String>) {
        try {
            settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", Json.encodeToString(folders))
            logger.d(tag, "Saved ${folders.size} watched GDrive folders")
        } catch (e: Exception) {
            logger.e(tag, "Failed to save watched GDrive folders", e)
        }
    }
}
