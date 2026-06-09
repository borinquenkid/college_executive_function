package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import com.russhwolf.settings.Settings

class DirectoryPreferencesManagerTest : StringSpec({

    "getWatchedLocalDirectories returns stored list" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        val dirs = listOf("/home/docs", "/home/downloads")
        val encoded = """["/home/docs","/home/downloads"]"""
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedLocalDirectories()

        result shouldBe dirs
    }

    "getWatchedLocalDirectories returns empty for blank setting" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns ""

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }

    "setWatchedLocalDirectories stores serialized list" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)

        val manager = DirectoryPreferencesManager(settings, logger)
        val dirs = listOf("/home/docs", "/home/downloads")

        manager.setWatchedLocalDirectories(dirs)

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", any()) }
    }

    "getWatchedGDriveFolders returns stored list" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        val folders = listOf("folder-id-1", "folder-id-2")
        val encoded = """["folder-id-1","folder-id-2"]"""
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns encoded

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedGDriveFolders()

        result shouldBe folders
    }

    "setWatchedGDriveFolders stores serialized list" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)

        val manager = DirectoryPreferencesManager(settings, logger)
        val folders = listOf("folder-id-1", "folder-id-2")

        manager.setWatchedGDriveFolders(folders)

        verify { settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", any()) }
    }

    "handles deserialization errors gracefully" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns "{invalid json}"

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
        verify { logger.e(any(), any(), any()) }
    }
})

