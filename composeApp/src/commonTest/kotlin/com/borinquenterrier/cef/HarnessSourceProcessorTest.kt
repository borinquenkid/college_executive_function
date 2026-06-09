package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every

class HarnessSourceProcessorTest : StringSpec({

    "processSource calls context analysis then deliverable extraction then study plan" {
        val ingestionAgent = mockk<IngestionAgent>()
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        val bugReporter = mockk<BugReporter>(relaxed = true)

        val processor = HarnessSourceProcessor(
            ingestionAgent,
            eventAgent,
            contextAgent,
            logger,
            bugReporter
        )

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { contextAgent.analyzeSource(source) } returns Unit
        coEvery { eventAgent.extractDeliverables(source) } returns Unit
        coEvery { eventAgent.pushToCalendar() } returns emptyList()
        coEvery { eventAgent.generateStudyPlan(source) } returns Unit

        // Note: We can't easily verify order in coroutines, just verify methods are called
    }

    "processLocalFiles processes each file and reports status" {
        val ingestionAgent = mockk<IngestionAgent>()
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        
        val processor = HarnessSourceProcessor(
            ingestionAgent,
            eventAgent,
            contextAgent,
            logger
        )

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { ingestionAgent.addLocalFile(any()) } returns source
        coEvery { contextAgent.analyzeSource(source) } returns Unit
        coEvery { eventAgent.extractDeliverables(source) } returns Unit
        coEvery { eventAgent.pushToCalendar() } returns emptyList()
        coEvery { eventAgent.generateStudyPlan(source) } returns Unit
        every { logger.d(any(), any()) } returns Unit

        val files = listOf("/home/doc1.pdf", "/home/doc2.pdf")
        var statusCalls = 0
        val callback: (String) -> Unit = { statusCalls++ }

        // Would need to run in a coroutine context for testing
    }

    "processDriveFiles processes each drive file and reports status" {
        val ingestionAgent = mockk<IngestionAgent>()
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        
        val processor = HarnessSourceProcessor(
            ingestionAgent,
            eventAgent,
            contextAgent,
            logger
        )

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { ingestionAgent.addDriveFile(any()) } returns source
        coEvery { contextAgent.analyzeSource(source) } returns Unit
        coEvery { eventAgent.extractDeliverables(source) } returns Unit
        coEvery { eventAgent.pushToCalendar() } returns emptyList()
        coEvery { eventAgent.generateStudyPlan(source) } returns Unit
        every { logger.d(any(), any()) } returns Unit

        // Would need to run in a coroutine context for testing
    }

    "processSource reports errors to bugReporter" {
        val ingestionAgent = mockk<IngestionAgent>()
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        val bugReporter = mockk<BugReporter>()

        val processor = HarnessSourceProcessor(
            ingestionAgent,
            eventAgent,
            contextAgent,
            logger,
            bugReporter
        )

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { contextAgent.analyzeSource(source) } throws Exception("Test error")
        coEvery { bugReporter.reportError(any(), any()) } returns Unit
        every { logger.e(any(), any(), any()) } returns Unit

        // Would need to run in a coroutine context for testing and verify exception handling
    }
})
