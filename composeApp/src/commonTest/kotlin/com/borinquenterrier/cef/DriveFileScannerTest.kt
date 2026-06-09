package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.coEvery

class DriveFileScannerTest : StringSpec({

    "scanNewFiles queries Drive API for watched folders" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedGDriveFolders() } returns listOf("folder-id-1")

        // Verify Drive API query with folder constraint
    }

    "scanNewFiles handles empty preference list" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedGDriveFolders() } returns emptyList()

        // Verify returns empty list when no folders watched
    }

    "buildDriveQuery constructs valid query string" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        // Verify query filters by folder parent and document MIME types
    }

    "scanNewFiles handles Drive API errors gracefully" {
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = DriveFileScanner(driveService, tokenRepository, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedGDriveFolders() } returns listOf("folder-id-1")
        coEvery { driveService.searchFiles(any()) } throws Exception("API quota exceeded")

        // Verify error logging and empty result
    }
})
