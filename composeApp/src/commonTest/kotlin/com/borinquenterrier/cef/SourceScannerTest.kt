package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class SourceScannerTest : StringSpec({

    "getWatchedLocalDirectories delegates to directoryPreferences" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()
        val driveFileScanner = mockk<DriveFileScanner>()

        every { directoryPreferences.getWatchedLocalDirectories() } returns listOf("/home/user")

        val scanner = SourceScanner(directoryPreferences, localFileScanner, driveFileScanner)
        scanner.getWatchedLocalDirectories() shouldBe listOf("/home/user")
    }

    "setWatchedLocalDirectories delegates to directoryPreferences" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()
        val driveFileScanner = mockk<DriveFileScanner>()

        every { directoryPreferences.setWatchedLocalDirectories(any()) } returns Unit

        val scanner = SourceScanner(directoryPreferences, localFileScanner, driveFileScanner)
        scanner.setWatchedLocalDirectories(listOf("/home/user"))
    }

    "getWatchedGDriveFolders delegates to directoryPreferences" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()
        val driveFileScanner = mockk<DriveFileScanner>()

        every { directoryPreferences.getWatchedGDriveFolders() } returns listOf(
            "folder1",
            "folder2"
        )

        val scanner = SourceScanner(directoryPreferences, localFileScanner, driveFileScanner)
        scanner.getWatchedGDriveFolders() shouldBe listOf("folder1", "folder2")
    }

    "setWatchedGDriveFolders delegates to directoryPreferences" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()
        val driveFileScanner = mockk<DriveFileScanner>()

        every { directoryPreferences.setWatchedGDriveFolders(any()) } returns Unit

        val scanner = SourceScanner(directoryPreferences, localFileScanner, driveFileScanner)
        scanner.setWatchedGDriveFolders(listOf("folder1"))
    }

    "scanNewLocalFiles delegates to localFileScanner" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()
        val driveFileScanner = mockk<DriveFileScanner>()

        coEvery { localFileScanner.scanNewFiles(any()) } returns listOf("/home/user/file1.pdf")

        val scanner = SourceScanner(directoryPreferences, localFileScanner, driveFileScanner)
        val result = scanner.scanNewLocalFiles(emptySet())

        result shouldBe listOf("/home/user/file1.pdf")
    }

    "scanNewDriveFiles delegates to driveFileScanner" {
        val directoryPreferences = mockk<DirectoryPreferencesManager>()
        val localFileScanner = mockk<LocalFileScanner>()
        val driveFileScanner = mockk<DriveFileScanner>()

        val driveFile = DriveFile(id = "file1", name = "doc.pdf", mimeType = "application/pdf")
        coEvery { driveFileScanner.scanNewFiles(any()) } returns listOf(driveFile)

        val scanner = SourceScanner(directoryPreferences, localFileScanner, driveFileScanner)
        val result = scanner.scanNewDriveFiles(emptySet())

        result shouldHaveSize 1
        result[0].id shouldBe "file1"
    }
})
