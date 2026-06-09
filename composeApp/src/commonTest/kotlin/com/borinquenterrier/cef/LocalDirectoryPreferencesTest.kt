package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import com.russhwolf.settings.Settings

class LocalDirectoryPreferencesTest : StringSpec({
    val settings = mockk<Settings>()
    val serializer = mockk<PreferenceSerializer>()
    val logger = mockk<Logger>(relaxed = true)
    val prefs = LocalDirectoryPreferences(settings, serializer, logger)

    "getWatchedDirectories returns stored list" {
        val dirs = listOf("/home/docs", "/home/downloads")
        val encoded = """["/home/docs","/home/downloads"]"""
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns dirs

        val result = prefs.getWatchedDirectories()

        result shouldBe dirs
    }

    "getWatchedDirectories returns empty for blank setting" {
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns ""
        every { serializer.deserializeDirectories("") } returns null

        val result = prefs.getWatchedDirectories()

        result.shouldBeEmpty()
    }

    "setWatchedDirectories stores serialized list" {
        val dirs = listOf("/home/docs", "/home/downloads")
        every { serializer.serializeDirectories(dirs) } returns """["/home/docs","/home/downloads"]"""
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedDirectories(dirs)

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", any()) }
        verify { logger.d(any(), any()) }
    }

    "handles deserialization errors gracefully" {
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns "{invalid json}"
        every { serializer.deserializeDirectories("{invalid json}") } returns null

        val result = prefs.getWatchedDirectories()

        result.shouldBeEmpty()
    }

    "logs on successful set operation" {
        val dirs = listOf("/path/one", "/path/two")
        every { serializer.serializeDirectories(dirs) } returns """["/path/one","/path/two"]"""
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedDirectories(dirs)

        verify { logger.d(any(), match { msg: String -> msg.contains("2") }) }
    }

    "handles single directory path" {
        val encoded = """["/home/single"]"""
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns encoded
        every { serializer.deserializeDirectories(encoded) } returns listOf("/home/single")

        val result = prefs.getWatchedDirectories()

        result shouldBe listOf("/home/single")
    }

    "handles empty list serialization" {
        every { serializer.serializeDirectories(emptyList()) } returns "[]"
        every { settings.putString(any(), any()) } returns Unit

        prefs.setWatchedDirectories(emptyList())

        verify { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", "[]") }
    }
})
