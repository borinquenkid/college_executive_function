package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

class LocalDirectoryPreferences(
    private val settings: Settings,
    private val serializer: PreferenceSerializer,
    private val logger: Logger
) {
    private val tag = "LocalDirectoryPreferences"
    private val key = "CEF_WATCHED_LOCAL_DIRECTORIES"

    fun getWatchedDirectories(): List<String> {
        val jsonString = settings.getString(key, "")
        return serializer.deserializeDirectories(jsonString) ?: emptyList()
    }

    fun setWatchedDirectories(dirs: List<String>) {
        val serialized = serializer.serializeDirectories(dirs)
        if (serialized.isNotEmpty()) {
            settings.putString(key, serialized)
            logger.d(tag, "Saved ${dirs.size} watched local directories")
        }
    }
}
