package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class DirectoryPreferencesManagerTest : StringSpec({

    "getWatchedLocalDirectories delegates to local preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>()
        val drivePrefs = mockk<DriveDirectoryPreferences>()

        val dirs = listOf("/home/docs", "/home/downloads")
        every { localPrefs.getWatchedDirectories() } returns dirs

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        val result = manager.getWatchedLocalDirectories()

        result shouldBe dirs
        verify { localPrefs.getWatchedDirectories() }
    }

    "getWatchedLocalDirectories returns empty from local preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>()
        val drivePrefs = mockk<DriveDirectoryPreferences>()

        every { localPrefs.getWatchedDirectories() } returns emptyList()

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }

    "setWatchedLocalDirectories delegates to local preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>(relaxed = true)
        val drivePrefs = mockk<DriveDirectoryPreferences>()

        val dirs = listOf("/home/docs", "/home/downloads")

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        manager.setWatchedLocalDirectories(dirs)

        verify { localPrefs.setWatchedDirectories(dirs) }
    }

    "getWatchedGDriveFolders delegates to drive preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>()
        val drivePrefs = mockk<DriveDirectoryPreferences>()

        val folders = listOf("folder-id-1", "folder-id-2")
        every { drivePrefs.getWatchedFolders() } returns folders

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        val result = manager.getWatchedGDriveFolders()

        result shouldBe folders
        verify { drivePrefs.getWatchedFolders() }
    }

    "setWatchedGDriveFolders delegates to drive preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>()
        val drivePrefs = mockk<DriveDirectoryPreferences>(relaxed = true)

        val folders = listOf("folder-id-1", "folder-id-2")

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        manager.setWatchedGDriveFolders(folders)

        verify { drivePrefs.setWatchedFolders(folders) }
    }

    "handles empty local directories" {
        val localPrefs = mockk<LocalDirectoryPreferences>()
        val drivePrefs = mockk<DriveDirectoryPreferences>()

        every { localPrefs.getWatchedDirectories() } returns emptyList()

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }

    "handles empty drive folders" {
        val localPrefs = mockk<LocalDirectoryPreferences>()
        val drivePrefs = mockk<DriveDirectoryPreferences>()

        every { drivePrefs.getWatchedFolders() } returns emptyList()

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        val result = manager.getWatchedGDriveFolders()

        result.shouldBeEmpty()
    }

    "coordinates multiple settings updates" {
        val localPrefs = mockk<LocalDirectoryPreferences>(relaxed = true)
        val drivePrefs = mockk<DriveDirectoryPreferences>(relaxed = true)

        val dirs = listOf("/path/one", "/path/two")
        val folders = listOf("folder1", "folder2")

        val manager = DirectoryPreferencesManager(localPrefs, drivePrefs)
        manager.setWatchedLocalDirectories(dirs)
        manager.setWatchedGDriveFolders(folders)

        verify { localPrefs.setWatchedDirectories(dirs) }
        verify { drivePrefs.setWatchedFolders(folders) }
    }
})

