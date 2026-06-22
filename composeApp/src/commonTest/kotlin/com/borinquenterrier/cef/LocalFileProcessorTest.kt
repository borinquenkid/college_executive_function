package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class LocalFileProcessorTest : StringSpec({

    "processLocalFiles processes each file through pipeline" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger)

        val source1 = mockk<SourceItem>(relaxed = true)
        val source2 = mockk<SourceItem>(relaxed = true)
        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")

        coEvery { ingestionAgent.addLocalFile("/home/doc1.pdf") } returns source1
        coEvery { ingestionAgent.addLocalFile("/home/doc2.pdf") } returns source2
        coEvery { pipeline.processSource(any()) } returns Unit

        val statusMessages = mutableListOf<String>()
        processor.processLocalFiles(files) { statusMessages.add(it) }

        coVerify(exactly = 2) { pipeline.processSource(any()) }
        statusMessages.size shouldBe 2
    }

    "processLocalFiles calls status callback for each file" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger)

        val source = mockk<SourceItem>(relaxed = true)
        val files = listOf("/home/doc1.pdf")

        coEvery { ingestionAgent.addLocalFile(any()) } returns source
        coEvery { pipeline.processSource(any()) } returns Unit

        var statusCalls = 0
        processor.processLocalFiles(files) { statusCalls++ }

        statusCalls shouldBe 1
    }

    "processLocalFiles with empty list skips loop body" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger)

        processor.processLocalFiles(emptyList()) {}

        coVerify(exactly = 0) { pipeline.processSource(any()) }
    }

    "processLocalFiles catches error and continues with non-null bugReporter" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)
        val bugReporter = mockk<BugReporter>(relaxed = true)

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger, bugReporter)

        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")
        coEvery { ingestionAgent.addLocalFile("/home/doc1.pdf") } throws Exception("Read error")
        coEvery { ingestionAgent.addLocalFile("/home/doc2.pdf") } returns mockk(relaxed = true)
        coEvery { pipeline.processSource(any()) } returns Unit

        processor.processLocalFiles(files) {}

        coVerify(exactly = 1) { pipeline.processSource(any()) }
        coVerify(exactly = 1) { bugReporter.reportError(any(), any()) }
    }

    "processLocalFiles catches error and continues with null bugReporter" {
        val ingestionAgent = mockk<IngestionAgent>()
        val pipeline = mockk<SourceProcessingPipeline>()
        val logger = mockk<Logger>(relaxed = true)

        val processor = LocalFileProcessor(ingestionAgent, pipeline, logger, bugReporter = null)

        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")
        coEvery { ingestionAgent.addLocalFile("/home/doc1.pdf") } throws Exception("Read error")
        coEvery { ingestionAgent.addLocalFile("/home/doc2.pdf") } returns mockk(relaxed = true)
        coEvery { pipeline.processSource(any()) } returns Unit

        processor.processLocalFiles(files) {}

        coVerify(exactly = 1) { pipeline.processSource(any()) }
    }
})
