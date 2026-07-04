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

        val dirs = listOf("/home/docs", "/home/downloads")
        every { localPrefs.getWatchedDirectories() } returns dirs

        val manager = DirectoryPreferencesManager(localPrefs)
        val result = manager.getWatchedLocalDirectories()

        result shouldBe dirs
        verify { localPrefs.getWatchedDirectories() }
    }

    "getWatchedLocalDirectories returns empty from local preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>()

        every { localPrefs.getWatchedDirectories() } returns emptyList()

        val manager = DirectoryPreferencesManager(localPrefs)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }

    "setWatchedLocalDirectories delegates to local preferences" {
        val localPrefs = mockk<LocalDirectoryPreferences>(relaxed = true)

        val dirs = listOf("/home/docs", "/home/downloads")

        val manager = DirectoryPreferencesManager(localPrefs)
        manager.setWatchedLocalDirectories(dirs)

        verify { localPrefs.setWatchedDirectories(dirs) }
    }

    "handles empty local directories" {
        val localPrefs = mockk<LocalDirectoryPreferences>()

        every { localPrefs.getWatchedDirectories() } returns emptyList()

        val manager = DirectoryPreferencesManager(localPrefs)
        val result = manager.getWatchedLocalDirectories()

        result.shouldBeEmpty()
    }
})
