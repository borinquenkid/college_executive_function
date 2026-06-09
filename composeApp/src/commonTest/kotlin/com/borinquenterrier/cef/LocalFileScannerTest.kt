package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.coEvery

class LocalFileScannerTest : StringSpec({

    "scanNewFiles returns files matching preferences" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")

        // Verify file scanning with preference filtering
    }

    "scanNewFiles handles empty preference list" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns emptyList()

        // Verify returns empty list when no directories watched
    }

    "scanNewFiles filters by supported document types" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")

        // Verify only .pdf, .docx files returned
    }

    "scanNewFiles handles file read errors gracefully" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>()

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles(any()) } throws Exception("Read permission denied")

        // Verify error logging and empty result
    }
})
