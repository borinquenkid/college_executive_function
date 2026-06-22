package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class DriveFileProcessorTest : StringSpec({

    "processDriveFiles processes each drive file through pipeline" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger)

        val driveFile1 = mockk<DriveFile>(relaxed = true) { every { name } returns "file1.pdf" }
        val driveFile2 = mockk<DriveFile>(relaxed = true) { every { name } returns "file2.docx" }
        val files = listOf(driveFile1, driveFile2)

        val source1 = mockk<SourceItem>(relaxed = true)
        val source2 = mockk<SourceItem>(relaxed = true)
        coEvery { ingestionAgent.addDriveFile(driveFile1) } returns source1
        coEvery { ingestionAgent.addDriveFile(driveFile2) } returns source2
        coEvery { pipeline.processSource(any()) } returns Unit

        val statusMessages = mutableListOf<String>()
        processor.processDriveFiles(files) { statusMessages.add(it) }

        coVerify(exactly = 2) { pipeline.processSource(any()) }
        statusMessages.size shouldBe 2
    }

    "processDriveFiles calls status callback for each file" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger)

        val driveFile = mockk<DriveFile>(relaxed = true) { every { name } returns "syllabus.pdf" }
        val files = listOf(driveFile)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { ingestionAgent.addDriveFile(driveFile) } returns source
        coEvery { pipeline.processSource(source) } returns Unit

        var statusCalls = 0
        processor.processDriveFiles(files) { statusCalls++ }

        statusCalls shouldBe 1
    }

    "processDriveFiles with empty list skips loop body" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger)

        processor.processDriveFiles(emptyList()) {}

        coVerify(exactly = 0) { pipeline.processSource(any()) }
    }

    "processDriveFiles catches error and continues with non-null bugReporter" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)
        val bugReporter = mockk<BugReporter>(relaxed = true)

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger, bugReporter)

        val driveFile1 = mockk<DriveFile>(relaxed = true) { every { name } returns "file1.pdf" }
        val driveFile2 = mockk<DriveFile>(relaxed = true) { every { name } returns "file2.docx" }
        val files = listOf(driveFile1, driveFile2)

        coEvery { ingestionAgent.addDriveFile(driveFile1) } throws Exception("Download failed")
        coEvery { ingestionAgent.addDriveFile(driveFile2) } returns mockk(relaxed = true)
        coEvery { pipeline.processSource(any()) } returns Unit

        processor.processDriveFiles(files) {}

        coVerify(exactly = 1) { pipeline.processSource(any()) }
        coVerify(exactly = 1) { bugReporter.reportError(any(), any()) }
    }

    "processDriveFiles catches error and continues with null bugReporter" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger, bugReporter = null)

        val driveFile1 = mockk<DriveFile>(relaxed = true) { every { name } returns "file1.pdf" }
        val driveFile2 = mockk<DriveFile>(relaxed = true) { every { name } returns "file2.docx" }
        val files = listOf(driveFile1, driveFile2)

        coEvery { ingestionAgent.addDriveFile(driveFile1) } throws Exception("Download failed")
        coEvery { ingestionAgent.addDriveFile(driveFile2) } returns mockk(relaxed = true)
        coEvery { pipeline.processSource(any()) } returns Unit

        processor.processDriveFiles(files) {}

        coVerify(exactly = 1) { pipeline.processSource(any()) }
    }
})
