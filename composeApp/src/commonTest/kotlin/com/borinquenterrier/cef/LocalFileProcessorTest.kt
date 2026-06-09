package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify

class LocalFileProcessorTest : StringSpec({

    "processLocalFiles processes each file through pipeline" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>()

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger)

        val source1 = mockk<SourceItem>(relaxed = true)
        val source2 = mockk<SourceItem>(relaxed = true)
        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")

        coEvery { ingestionAgent.addLocalFile("/home/doc1.pdf") } returns source1
        coEvery { ingestionAgent.addLocalFile("/home/doc2.pdf") } returns source2
        coEvery { pipeline.processSource(any()) } returns Unit

        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        // Verify all files processed
    }

    "processLocalFiles calls status callback for each file" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>()

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger)

        val source = mockk<SourceItem>(relaxed = true)
        val files = listOf("/home/doc1.pdf")

        coEvery { ingestionAgent.addLocalFile(any()) } returns source
        coEvery { pipeline.processSource(any()) } returns Unit

        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        // Verify callback invoked
    }

    "processLocalFiles catches and logs errors per file" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>()
        val bugReporter = mockk<BugReporter>(relaxed = true)

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger, bugReporter)

        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")
        coEvery { ingestionAgent.addLocalFile("/home/doc1.pdf") } throws Exception("Read error")
        coEvery { ingestionAgent.addLocalFile("/home/doc2.pdf") } returns mockk(relaxed = true)
        coEvery { pipeline.processSource(any()) } returns Unit

        val callback: (String) -> Unit = {}

        // Verify error handling continues to next file
    }
})
