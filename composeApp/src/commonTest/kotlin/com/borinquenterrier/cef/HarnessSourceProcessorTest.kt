package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class HarnessSourceProcessorTest : StringSpec({

    "processSource delegates to pipeline" {
        val pipeline = mockk<SourceProcessingPipeline>()
        val localFileProcessor = mockk<LocalFileProcessor>()
        val driveFileProcessor = mockk<DriveFileProcessor>()
        val logger = mockk<Logger>(relaxed = true)

        val processor =
            HarnessSourceProcessor(pipeline, localFileProcessor, driveFileProcessor, logger)

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { pipeline.processSource(source) } returns Unit

        processor.processSource(source)

        coVerify(exactly = 1) { pipeline.processSource(source) }
    }

    "processLocalFiles delegates to LocalFileProcessor" {
        val pipeline = mockk<SourceProcessingPipeline>()
        val localFileProcessor = mockk<LocalFileProcessor>()
        val driveFileProcessor = mockk<DriveFileProcessor>()
        val logger = mockk<Logger>(relaxed = true)

        val processor =
            HarnessSourceProcessor(pipeline, localFileProcessor, driveFileProcessor, logger)

        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")
        val callback: (String) -> Unit = {}
        coEvery { localFileProcessor.processLocalFiles(files, any()) } returns Unit

        processor.processLocalFiles(files, callback)

        coVerify(exactly = 1) { localFileProcessor.processLocalFiles(files, any()) }
    }

    "processDriveFiles delegates to DriveFileProcessor" {
        val pipeline = mockk<SourceProcessingPipeline>()
        val localFileProcessor = mockk<LocalFileProcessor>()
        val driveFileProcessor = mockk<DriveFileProcessor>()
        val logger = mockk<Logger>(relaxed = true)

        val processor =
            HarnessSourceProcessor(pipeline, localFileProcessor, driveFileProcessor, logger)

        val driveFile1 = mockk<DriveFile>(relaxed = true)
        val driveFile2 = mockk<DriveFile>(relaxed = true)
        val files = listOf(driveFile1, driveFile2)
        val callback: (String) -> Unit = {}
        coEvery { driveFileProcessor.processDriveFiles(files, any()) } returns Unit

        processor.processDriveFiles(files, callback)

        coVerify(exactly = 1) { driveFileProcessor.processDriveFiles(files, any()) }
    }
})
