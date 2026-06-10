package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class DriveDirectoryPreferencesTest : StringSpec({
    val settings = mockk<Settings>()
    val serializer = mockk<PreferenceSerializer>()
    val logger = mockk<Logger>(relaxed = true)
    val prefs = DriveDirectoryPreferences(settings, serializer, logger)

    "getWatchedFolders returns stored list" {
        val folders = listOf("folder-id-1", "folder-id-2")
        val encoded = """["folder-id-1","folder-id-2"]"""
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns folders

        val result = prefs.getWatchedFolders()

        result shouldBe folders
    }

    "getWatchedFolders returns empty for blank setting" {
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns ""
        every { serializer.deserializeDirectories("") } returns null

        val result = prefs.getWatchedFolders()

        result.shouldBeEmpty()
    }

    "setWatchedFolders stores serialized list" {
        val folders = listOf("folder-id-1", "folder-id-2")
        every { serializer.serializeDirectories(folders) } returns """["folder-id-1","folder-id-2"]"""
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedFolders(folders)

        verify { settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", any()) }
        verify { logger.d(any(), any()) }
    }

    "handles deserialization errors gracefully" {
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns "corrupted[["
        every { serializer.deserializeDirectories("corrupted[[") } returns null

        val result = prefs.getWatchedFolders()

        result.shouldBeEmpty()
    }

    "logs on successful set operation" {
        val folders = listOf("folder1", "folder2", "folder3")
        every { serializer.serializeDirectories(folders) } returns """["folder1","folder2","folder3"]"""
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedFolders(folders)

        verify { logger.d(any(), match { msg: String -> msg.contains("3") }) }
    }

    "handles single folder ID" {
        val encoded = """["single-folder-id"]"""
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns listOf("single-folder-id")

        val result = prefs.getWatchedFolders()

        result shouldBe listOf("single-folder-id")
    }

    "handles empty list serialization" {
        every { serializer.serializeDirectories(emptyList()) } returns "[]"
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedFolders(emptyList())

        verify { settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", "[]") }
    }

    "handles multiple folder updates" {
        val folders1 = listOf("folder1", "folder2")
        val folders2 = listOf("folder1", "folder2", "folder3")
        every { serializer.serializeDirectories(folders1) } returns """["folder1","folder2"]"""
        every { serializer.serializeDirectories(folders2) } returns """["folder1","folder2","folder3"]"""
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedFolders(folders1)
        prefs.setWatchedFolders(folders2)

        verify(atLeast = 2) { settings.putString(any(), any()) }
    }
})
