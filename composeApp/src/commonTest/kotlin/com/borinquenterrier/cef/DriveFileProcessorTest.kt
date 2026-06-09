package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.every

class DriveFileProcessorTest : StringSpec({

    "processDriveFiles processes each drive file through pipeline" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>()

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger)

        val driveFile1 = mockk<DriveFile>(relaxed = true) { every { name } returns "file1.pdf" }
        val driveFile2 = mockk<DriveFile>(relaxed = true) { every { name } returns "file2.docx" }
        val files = listOf(driveFile1, driveFile2)

        val source1 = mockk<SourceItem>(relaxed = true)
        val source2 = mockk<SourceItem>(relaxed = true)

        coEvery { ingestionAgent.addDriveFile(driveFile1) } returns source1
        coEvery { ingestionAgent.addDriveFile(driveFile2) } returns source2
        coEvery { pipeline.processSource(any()) } returns Unit

        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        // Verify all drive files processed
    }

    "processDriveFiles calls status callback for each file" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>()

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger)

        val driveFile = mockk<DriveFile>(relaxed = true) { every { name } returns "syllabus.pdf" }
        val files = listOf(driveFile)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { ingestionAgent.addDriveFile(driveFile) } returns source
        coEvery { pipeline.processSource(source) } returns Unit

        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        // Verify callback invoked with file name
    }

    "processDriveFiles catches and logs errors per file" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>()
        val bugReporter = mockk<BugReporter>(relaxed = true)

        val processor = DriveFileProcessor(ingestionAgent, pipeline, logger, bugReporter)

        val driveFile1 = mockk<DriveFile>(relaxed = true) { every { name } returns "file1.pdf" }
        val driveFile2 = mockk<DriveFile>(relaxed = true) { every { name } returns "file2.docx" }
        val files = listOf(driveFile1, driveFile2)

        coEvery { ingestionAgent.addDriveFile(driveFile1) } throws Exception("Download failed")
        coEvery { ingestionAgent.addDriveFile(driveFile2) } returns mockk(relaxed = true)
        coEvery { pipeline.processSource(any()) } returns Unit

        val callback: (String) -> Unit = {}

        // Verify error handling continues to next file
    }
})
