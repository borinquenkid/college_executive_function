package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import com.russhwolf.settings.Settings

class DirectoryPreferencesManagerTest : StringSpec({

    "getWatchedLocalDirectories returns stored list" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        val dirs = listOf("/home/docs", "/home/downloads")
        val encoded = """["/home/docs","/home/downloads"]"""
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns dirs

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = manager.getWatchedLocalDirectories()

        result shouldBe dirs
    }

    "getWatchedLocalDirectories returns empty for blank setting" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns ""
        every { serializer.deserializeDirectories("") } returns null

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }

    "setWatchedLocalDirectories stores serialized list" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        val dirs = listOf("/home/docs", "/home/downloads")
        every { serializer.serializeDirectories(dirs) } returns """["/home/docs","/home/downloads"]"""

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        manager.setWatchedLocalDirectories(dirs)

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", any()) }
    }

    "getWatchedGDriveFolders returns stored list" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        val folders = listOf("folder-id-1", "folder-id-2")
        val encoded = """["folder-id-1","folder-id-2"]"""
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns folders

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = manager.getWatchedGDriveFolders()

        result shouldBe folders
    }

    "setWatchedGDriveFolders stores serialized list" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        val folders = listOf("folder-id-1", "folder-id-2")
        every { serializer.serializeDirectories(folders) } returns """["folder-id-1","folder-id-2"]"""

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        manager.setWatchedGDriveFolders(folders)

        verify { settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", any()) }
    }

    "handles deserialization errors gracefully on local dirs" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns "{invalid json}"
        every { serializer.deserializeDirectories("{invalid json}") } returns null

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }

    "handles deserialization errors gracefully on drive folders" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns "corrupted[["
        every { serializer.deserializeDirectories("corrupted[[") } returns null

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = manager.getWatchedGDriveFolders()

        result.shouldBeEmpty()
    }

    "round-trip serialization preserves data" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        val dirs = listOf("/path/one", "/path/two", "/path/three")
        val encoded = """["/path/one","/path/two","/path/three"]"""
        every { serializer.serializeDirectories(dirs) } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns dirs

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        manager.setWatchedLocalDirectories(dirs)

        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded
        val newManager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = newManager.getWatchedLocalDirectories()

        result shouldBe dirs
    }

    "handles single directory path" {
        val settings = mockk<Settings>()
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        val encoded = """["/home/single"]"""
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns listOf("/home/single")

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        val result = manager.getWatchedLocalDirectories()

        result.shouldContainAll(listOf("/home/single"))
    }

    "setWatchedLocalDirectories can clear list with empty list" {
        val settings = mockk<Settings>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val serializer = mockk<PreferenceSerializer>()

        every { serializer.serializeDirectories(emptyList()) } returns "[]"

        val manager = DirectoryPreferencesManager(settings, serializer, logger)
        manager.setWatchedLocalDirectories(emptyList())

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", "[]") }
    }
})

