package com.borinquenterrier.cef

/**
 * Lightweight facade coordinating directory preferences for local file sources.
 */
class DirectoryPreferencesManager(
    private val localPreferences: LocalDirectoryPreferences
) {

    fun getWatchedLocalDirectories(): List<String> {
        return localPreferences.getWatchedDirectories()
    }

    fun setWatchedLocalDirectories(dirs: List<String>) {
        localPreferences.setWatchedDirectories(dirs)
    }
}
