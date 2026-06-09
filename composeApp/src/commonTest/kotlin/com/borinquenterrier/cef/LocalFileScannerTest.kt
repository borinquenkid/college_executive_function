package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest

class LocalFileScannerTest : StringSpec({

    "scanNewFiles returns files matching preferences" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles("/home/docs") } returns listOf("/home/docs/file1.pdf", "/home/docs/file2.docx")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldHaveSize(2)
        }
    }

    "scanNewFiles deduplicates against existing URIs" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles("/home/docs") } returns listOf("/home/docs/file1.pdf", "/home/docs/file2.docx")

        val existingUris = setOf("/home/docs/file1.pdf")

        runTest {
            val result = scanner.scanNewFiles(existingUris)
            result.shouldHaveSize(1)
        }
    }

    "scanNewFiles handles empty preference list" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns emptyList()

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldBeEmpty()
        }
    }

    "scanNewFiles handles file read errors gracefully" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        coEvery { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles("/home/docs") } throws Exception("Read permission denied")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldBeEmpty()
        }
    }
})
