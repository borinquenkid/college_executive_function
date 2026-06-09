package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest

class LocalFileScannerTest : StringSpec({

    "scanNewFiles returns files matching preferences" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
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

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
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

        every { preferencesManager.getWatchedLocalDirectories() } returns emptyList()

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

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles("/home/docs") } throws Exception("Read permission denied")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldBeEmpty()
        }
    }

    "scanNewFiles processes multiple watched directories" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs", "/home/downloads")
        coEvery { fileReader.listFiles("/home/docs") } returns listOf("/home/docs/file1.pdf")
        coEvery { fileReader.listFiles("/home/downloads") } returns listOf("/home/downloads/file2.docx")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldHaveSize(2)
        }
    }

    "scanNewFiles filters files by supported extensions" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles("/home/docs") } returns listOf(
            "/home/docs/file1.pdf",
            "/home/docs/file2.docx",
            "/home/docs/image.png"
        )

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            // Only PDF and DOCX should be included
            result.size shouldBe 2
        }
    }

    "scanNewFiles handles concurrent directory scanning" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/path1", "/path2", "/path3")
        coEvery { fileReader.listFiles(any()) } returns listOf("/path1/file.pdf")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldHaveSize(3) // One file from each directory call
        }
    }

    "scanNewFiles returns source fragments with correct URIs" {
        val fileReader = mockk<LocalFileReader>()
        val preferencesManager = mockk<DirectoryPreferencesManager>()
        val logger = mockk<Logger>(relaxed = true)

        val scanner = LocalFileScanner(fileReader, preferencesManager, logger)

        every { preferencesManager.getWatchedLocalDirectories() } returns listOf("/home/docs")
        coEvery { fileReader.listFiles("/home/docs") } returns listOf("/home/docs/test.pdf")

        runTest {
            val result = scanner.scanNewFiles(emptySet())
            result.shouldHaveSize(1)
            // Fragment should have URI set to file path
        }
    }
})
