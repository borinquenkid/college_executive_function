package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * Manages persistent preferences for watched directories and Google Drive folders.
 * Delegates serialization to PreferenceSerializer.
 */
class DirectoryPreferencesManager(
    private val settings: Settings,
    private val serializer: PreferenceSerializer,
    private val logger: Logger
) {
    private val tag = "DirectoryPreferencesManager"

    fun getWatchedLocalDirectories(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "")
        return serializer.deserializeDirectories(jsonString) ?: emptyList()
    }

    fun setWatchedLocalDirectories(dirs: List<String>) {
        val serialized = serializer.serializeDirectories(dirs)
        if (serialized.isNotEmpty()) {
            settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", serialized)
            logger.d(tag, "Saved ${dirs.size} watched local directories")
        }
    }

    fun getWatchedGDriveFolders(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "")
        return serializer.deserializeDirectories(jsonString) ?: emptyList()
    }

    fun setWatchedGDriveFolders(folders: List<String>) {
        val serialized = serializer.serializeDirectories(folders)
        if (serialized.isNotEmpty()) {
            settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", serialized)
            logger.d(tag, "Saved ${folders.size} watched GDrive folders")
        }
    }
}
