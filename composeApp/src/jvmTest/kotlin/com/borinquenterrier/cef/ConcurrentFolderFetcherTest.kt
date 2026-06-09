package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ConcurrentFolderFetcherTest : FunSpec({
    val driveService = mockk<GoogleDriveService>()
    val queryBuilder = mockk<DriveQueryBuilder>()
    val logger = mockk<Logger>(relaxed = true)
    val fetcher = ConcurrentFolderFetcher(driveService, queryBuilder, logger)

    test("fetchFromFolders returns aggregated files from multiple folders") {
        val file1: DriveFile = mockk()
        val file2: DriveFile = mockk()
        val file3: DriveFile = mockk()

        coEvery { queryBuilder.buildQueryForFolder("folder1") } returns "query1"
        coEvery { queryBuilder.buildQueryForFolder("folder2") } returns "query2"
        coEvery { driveService.listFiles("query1") } returns listOf(file1, file2)
        coEvery { driveService.listFiles("query2") } returns listOf(file3)

        val result = fetcher.fetchFromFolders(listOf("folder1", "folder2"))

        result shouldHaveSize 3
        result shouldContainExactly listOf(file1, file2, file3)
    }

    test("fetchFromFolders continues on single folder failure") {
        val file1: DriveFile = mockk()

        coEvery { queryBuilder.buildQueryForFolder("folder1") } returns "query1"
        coEvery { queryBuilder.buildQueryForFolder("folder2") } returns "query2"
        coEvery { driveService.listFiles("query1") } throws RuntimeException("API error")
        coEvery { driveService.listFiles("query2") } returns listOf(file1)

        val result = fetcher.fetchFromFolders(listOf("folder1", "folder2"))

        result shouldContainExactly listOf(file1)
    }

    test("fetchFromFolders returns empty list when all folders fail") {
        coEvery { queryBuilder.buildQueryForFolder("folder1") } returns "query1"
        coEvery { queryBuilder.buildQueryForFolder("folder2") } returns "query2"
        coEvery { driveService.listFiles("query1") } throws RuntimeException("Error 1")
        coEvery { driveService.listFiles("query2") } throws RuntimeException("Error 2")

        val result = fetcher.fetchFromFolders(listOf("folder1", "folder2"))

        result.shouldBeEmpty()
    }

    test("fetchFromFolders handles empty folder list") {
        val result = fetcher.fetchFromFolders(emptyList())

        result.shouldBeEmpty()
    }

    test("fetchFromFolders logs failures per folder") {
        coEvery { queryBuilder.buildQueryForFolder("folder1") } returns "query1"
        coEvery { driveService.listFiles("query1") } throws RuntimeException("API error")

        fetcher.fetchFromFolders(listOf("folder1"))

        coVerify { logger.e(any(), any<String>(), any<Exception>()) }
    }

    test("fetchFromFolders executes queries from queryBuilder") {
        val file: DriveFile = mockk()

        coEvery { queryBuilder.buildQueryForFolder("folder1") } returns "custom_query"
        coEvery { driveService.listFiles("custom_query") } returns listOf(file)

        fetcher.fetchFromFolders(listOf("folder1"))

        coVerify { queryBuilder.buildQueryForFolder("folder1") }
        coVerify { driveService.listFiles("custom_query") }
    }
})
