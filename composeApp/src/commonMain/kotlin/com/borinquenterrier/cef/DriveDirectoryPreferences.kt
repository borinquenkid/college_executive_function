package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

class DriveDirectoryPreferences(
    private val settings: Settings,
    private val serializer: PreferenceSerializer,
    private val logger: Logger
) {
    private val tag = "DriveDirectoryPreferences"
    private val key = "CEF_WATCHED_GDRIVE_FOLDERS"

    fun getWatchedFolders(): List<String> {
        val jsonString = settings.getString(key, "")
        return serializer.deserializeDirectories(jsonString) ?: emptyList()
    }

    fun setWatchedFolders(folders: List<String>) {
        val serialized = serializer.serializeDirectories(folders)
        if (serialized.isNotEmpty()) {
            settings.putString(key, serialized)
            logger.d(tag, "Saved ${folders.size} watched GDrive folders")
        }
    }
}
