package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class SourceScannerTest : StringSpec({

    "getWatchedLocalDirectories delegates to directoryPreferences" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()

        every { directoryPreferences.getWatchedLocalDirectories() } returns listOf("/home/user")

        val scanner = SourceScanner(directoryPreferences, localFileScanner)
        scanner.getWatchedLocalDirectories() shouldBe listOf("/home/user")
    }

    "setWatchedLocalDirectories delegates to directoryPreferences" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()

        every { directoryPreferences.setWatchedLocalDirectories(any()) } returns Unit

        val scanner = SourceScanner(directoryPreferences, localFileScanner)
        scanner.setWatchedLocalDirectories(listOf("/home/user"))
    }

    "scanNewLocalFiles delegates to localFileScanner" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()

        coEvery { localFileScanner.scanNewFiles(any()) } returns listOf("/home/user/file1.pdf")

        val scanner = SourceScanner(directoryPreferences, localFileScanner)
        val result = scanner.scanNewLocalFiles(emptySet())

        result shouldBe listOf("/home/user/file1.pdf")
    }
})
