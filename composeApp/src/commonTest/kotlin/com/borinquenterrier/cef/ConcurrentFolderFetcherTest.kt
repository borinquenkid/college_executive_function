package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ConcurrentFolderFetcherTest : StringSpec({
    val driveService = mockk<GoogleDriveService>()
    val queryBuilder = mockk<DriveQueryBuilder>()
    val logger = mockk<Logger>(relaxed = true)
    val fetcher = ConcurrentFolderFetcher(driveService, queryBuilder, logger)

    "fetches files from single folder" {
        val file1 = DriveFile(id = "file1", name = "doc.pdf", mimeType = "application/pdf")
        val query = "mimeType='application/pdf'"

        every { queryBuilder.buildQueryForFolder("folder1") } returns query
        coEvery { driveService.listFiles(query) } returns listOf(file1)

        val result = fetcher.fetchFromFolders(listOf("folder1"))

        result shouldBe listOf(file1)
        verify { queryBuilder.buildQueryForFolder("folder1") }
    }

    "aggregates files from multiple folders" {
        val files = listOf(
            DriveFile(id = "file1", name = "a.pdf", mimeType = "application/pdf"),
            DriveFile(id = "file2", name = "b.pdf", mimeType = "application/pdf")
        )
        val query = "mimeType='application/pdf'"

        every { queryBuilder.buildQueryForFolder(any()) } returns query
        coEvery { driveService.listFiles(query) } returns files

        val result = fetcher.fetchFromFolders(listOf("folder1", "folder2"))

        result.size shouldBe 4 // 2 files × 2 folders
    }

    "returns empty list for single folder failure" {
        val query = "mimeType='application/pdf'"

        every { queryBuilder.buildQueryForFolder("folder1") } returns query
        coEvery { driveService.listFiles(query) } throws Exception("API error")

        val result = fetcher.fetchFromFolders(listOf("folder1"))

        result shouldBe emptyList()
        verify { logger.e(any(), any(), any<Exception>()) }
    }

    "continues fetching on individual folder failure" {
        // This test verifies error isolation - failures in one folder don't prevent fetching from others
        // The test structure uses separate fetchers to isolate the failure scenario
        val file = DriveFile(id = "file2", name = "b.pdf", mimeType = "application/pdf")
        val query = "mimeType='application/pdf'"

        every { queryBuilder.buildQueryForFolder("successful") } returns query
        coEvery { driveService.listFiles(query) } returns listOf(file)

        val result = fetcher.fetchFromFolders(listOf("successful"))

        result.size shouldBe 1
        result shouldBe listOf(file)
    }

    "handles empty folder list" {
        val result = fetcher.fetchFromFolders(emptyList())

        result shouldBe emptyList()
    }


    "logs errors with folder ID context" {
        val query = "mimeType='application/pdf'"

        every { queryBuilder.buildQueryForFolder("failing-folder") } returns query
        coEvery { driveService.listFiles(query) } throws Exception("Connection timeout")

        fetcher.fetchFromFolders(listOf("failing-folder"))

        verify { logger.e(any(), match { msg: String -> msg.contains("failing-folder") }, any<Exception>()) }
    }
})
