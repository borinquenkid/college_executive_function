package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

/**
 * Lightweight facade coordinating directory preferences across local and Google Drive sources.
 * Delegates to specialized preference managers for local and GDrive directories.
 */
class DirectoryPreferencesManager(
    private val localPreferences: LocalDirectoryPreferences,
    private val drivePreferences: DriveDirectoryPreferences
) {

    fun getWatchedLocalDirectories(): List<String> {
        return localPreferences.getWatchedDirectories()
    }

    fun setWatchedLocalDirectories(dirs: List<String>) {
        localPreferences.setWatchedDirectories(dirs)
    }

    fun getWatchedGDriveFolders(): List<String> {
        return drivePreferences.getWatchedFolders()
    }

    fun setWatchedGDriveFolders(folders: List<String>) {
        drivePreferences.setWatchedFolders(folders)
    }
}
