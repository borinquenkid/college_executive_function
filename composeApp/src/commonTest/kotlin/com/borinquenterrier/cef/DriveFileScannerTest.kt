package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest

class DriveFileScannerTest : StringSpec({

    "scanNewFiles queries Drive API for watched folders" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        every { tokenRepository.hasTokens() } returns true
        coEvery { preferencesManager.getWatchedGDriveFolders() } returns listOf("folder-id-1")
        coEvery { driveService.listFiles(any()) } returns listOf(
            mockk<DriveFile>(relaxed = true) { every { id } returns "file1" }
        )

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldHaveSize(1)
        }
    }

    "scanNewFiles skips when auth not available" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        every { tokenRepository.hasTokens() } returns false

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldBeEmpty()
        }
    }

    "scanNewFiles handles empty preference list" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        every { tokenRepository.hasTokens() } returns true
        coEvery { preferencesManager.getWatchedGDriveFolders() } returns emptyList()

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldBeEmpty()
        }
    }

    "scanNewFiles deduplicates against existing URIs" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        every { tokenRepository.hasTokens() } returns true
        coEvery { preferencesManager.getWatchedGDriveFolders() } returns listOf("folder-id-1")
        coEvery { driveService.listFiles(any()) } returns listOf(
            mockk<DriveFile>(relaxed = true) { every { id } returns "file1" },
            mockk<DriveFile>(relaxed = true) { every { id } returns "file2" }
        )

        val existingUris = setOf("google_drive://file1")

        runTest {
            val result = scanner.scanNewFiles(existingUris)
            result.shouldHaveSize(1)
        }
    }

    "scanNewFiles handles Drive API errors gracefully" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        every { tokenRepository.hasTokens() } returns true
        coEvery { preferencesManager.getWatchedGDriveFolders() } returns listOf("folder-id-1")
        coEvery { driveService.listFiles(any()) } throws Exception("API quota exceeded")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldBeEmpty()
        }
    }
})
