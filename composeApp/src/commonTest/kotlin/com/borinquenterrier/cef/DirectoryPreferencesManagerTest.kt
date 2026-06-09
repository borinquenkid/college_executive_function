package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.slot
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
        val slot = slot<String>()

        val manager = DirectoryPreferencesManager(settings, logger)
        val dirs = listOf("/home/docs", "/home/downloads")

        manager.setWatchedLocalDirectories(dirs)

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", capture(slot)) }
        slot.captured.contains("/home/docs") shouldBe true
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

    "handles deserialization errors gracefully on local dirs" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns "{invalid json}"

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
        verify { logger.e(any(), any(), any()) }
    }

    "handles deserialization errors gracefully on drive folders" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns "corrupted[["

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedGDriveFolders()

        result.shouldBeEmpty()
    }

    "round-trip serialization preserves data" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val slots = slot<String>()

        val manager = DirectoryPreferencesManager(settings, logger)
        val dirs = listOf("/path/one", "/path/two", "/path/three")

        manager.setWatchedLocalDirectories(dirs)

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", capture(slots)) }
        val serialized = slots.captured

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns serialized
        val newManager = DirectoryPreferencesManager(settings, logger)
        val result = newManager.getWatchedLocalDirectories()

        result shouldBe dirs
    }

    "handles single directory path" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)

        val encoded = """["/home/single"]"""
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded

        val manager = DirectoryPreferencesManager(settings, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldContainAll(listOf("/home/single"))
    }

    "setWatchedLocalDirectories can clear list with empty list" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)

        val manager = DirectoryPreferencesManager(settings, logger)
        manager.setWatchedLocalDirectories(emptyList())

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", "[]") }
    }
})

