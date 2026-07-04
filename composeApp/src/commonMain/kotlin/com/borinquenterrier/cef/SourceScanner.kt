package com.borinquenterrier.cef

/**
 * Lightweight facade orchestrating concurrent scanning of local and GDrive sources.
 * Delegates directory preferences, local file scanning, and GDrive scanning to specialized services.
 */
class SourceScanner(
    private val directoryPreferences: DirectoryPreferencesManager,
    private val localFileScanner: LocalFileScanner
) {
    fun getWatchedLocalDirectories(): List<String> =
        directoryPreferences.getWatchedLocalDirectories()

    fun setWatchedLocalDirectories(dirs: List<String>) =
        directoryPreferences.setWatchedLocalDirectories(dirs)

    suspend fun scanNewLocalFiles(existingUris: Set<String>): List<String> =
        localFileScanner.scanNewFiles(existingUris)
}

