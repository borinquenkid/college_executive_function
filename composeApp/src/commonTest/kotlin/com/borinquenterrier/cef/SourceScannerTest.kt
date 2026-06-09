package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.every
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SourceScannerTest : StringSpec({

    "getWatchedLocalDirectories returns empty list when not set" {
        val settings = mockk<Settings>()
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns ""
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val logger = mockk<Logger>()
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        scanner.getWatchedLocalDirectories().shouldBeEmpty()
    }

    "getWatchedLocalDirectories returns stored directories" {
        val dirs = listOf("/home/user/docs", "/tmp")
        val settings = mockk<Settings>()
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns Json.encodeToString(dirs)
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val logger = mockk<Logger>()
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        scanner.getWatchedLocalDirectories() shouldBe dirs
    }

    "setWatchedLocalDirectories stores to settings" {
        val settings = mockk<Settings>()
        every { settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", any()) } returns Unit
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val logger = mockk<Logger>()
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        scanner.setWatchedLocalDirectories(listOf("/path1", "/path2"))
    }

    "getWatchedGDriveFolders returns empty list when not set" {
        val settings = mockk<Settings>()
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns ""
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val logger = mockk<Logger>()
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        scanner.getWatchedGDriveFolders().shouldBeEmpty()
    }

    "getWatchedGDriveFolders returns stored folders" {
        val folders = listOf("folder1", "folder2")
        val settings = mockk<Settings>()
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns Json.encodeToString(folders)
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val logger = mockk<Logger>()
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        scanner.getWatchedGDriveFolders() shouldBe folders
    }

    "scanNewLocalFiles returns empty list when no files" {
        val settings = mockk<Settings>()
        every { settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "") } returns Json.encodeToString(emptyList<String>())
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        val logger = mockk<Logger>()
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        // Empty watched dirs means empty result
        scanner.scanNewLocalFiles(emptySet()).shouldBeEmpty()
    }

    "scanNewDriveFiles returns empty list when auth not available" {
        val settings = mockk<Settings>()
        every { settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "") } returns Json.encodeToString(listOf("folder1"))
        
        val fileReader = mockk<LocalFileReader>()
        val driveService = mockk<GoogleDriveService>()
        val tokenRepository = mockk<GoogleTokenRepository>()
        every { tokenRepository.hasTokens() } returns false
        
        val logger = mockk<Logger>()
        every { logger.d(any(), any()) } returns Unit
        
        val scanner = SourceScanner(fileReader, driveService, tokenRepository, settings, logger)
        scanner.scanNewDriveFiles(emptySet()).shouldBeEmpty()
    }
})
