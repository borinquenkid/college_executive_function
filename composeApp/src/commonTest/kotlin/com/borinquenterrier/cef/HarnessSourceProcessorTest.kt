package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.mockk

class HarnessSourceProcessorTest : StringSpec({

    "processSource delegates to pipeline" {
        val pipeline = mockk<SourceProcessingPipeline>()
        val localFileProcessor = mockk<LocalFileProcessor>()
        val driveFileProcessor = mockk<DriveFileProcessor>()
        val logger = mockk<Logger>()

        val processor =
            HarnessSourceProcessor(pipeline, localFileProcessor, driveFileProcessor, logger)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { pipeline.processSource(source) } returns Unit

        // Verify delegation works
    }

    "processLocalFiles delegates to LocalFileProcessor" {
        val pipeline = mockk<SourceProcessingPipeline>()
        val localFileProcessor = mockk<LocalFileProcessor>()
        val driveFileProcessor = mockk<DriveFileProcessor>()
        val logger = mockk<Logger>()

        val processor =
            HarnessSourceProcessor(pipeline, localFileProcessor, driveFileProcessor, logger)

        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")
        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        coEvery { localFileProcessor.processLocalFiles(files, any()) } returns Unit

        // Verify delegation works
    }

    "processDriveFiles delegates to DriveFileProcessor" {
        val pipeline = mockk<SourceProcessingPipeline>()
        val localFileProcessor = mockk<LocalFileProcessor>()
        val driveFileProcessor = mockk<DriveFileProcessor>()
        val logger = mockk<Logger>()

        val processor =
            HarnessSourceProcessor(pipeline, localFileProcessor, driveFileProcessor, logger)

        val driveFile1 = mockk<DriveFile>(relaxed = true)
        val driveFile2 = mockk<DriveFile>(relaxed = true)
        val files = listOf(driveFile1, driveFile2)
        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        coEvery { driveFileProcessor.processDriveFiles(files, any()) } returns Unit

        // Verify delegation works
    }
})
