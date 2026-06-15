package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.mockk

class SourceProcessingPipelineTest : StringSpec({

    "processSource completes all 3 analysis steps" {
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        val bugReporter = mockk<BugReporter>(relaxed = true)

        val pipeline = SourceProcessingPipeline(
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

        // Verify all steps are called
    }

    "processSource reports errors to bugReporter" {
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()
        val bugReporter = mockk<BugReporter>()

        val pipeline = SourceProcessingPipeline(
            eventAgent,
            contextAgent,
            logger,
            bugReporter
        )

        val source = mockk<SourceItem>(relaxed = true)
        val testException = Exception("Analysis failed")
        coEvery { contextAgent.analyzeSource(source) } throws testException
        coEvery { bugReporter.reportError(testException, any()) } returns Unit

        // Verify error handling
    }

    "processSource logs each step" {
        val eventAgent = mockk<EventAgent>()
        val contextAgent = mockk<ContextAgent>()
        val logger = mockk<Logger>()

        val pipeline = SourceProcessingPipeline(
            eventAgent,
            contextAgent,
            logger
        )

        val source = mockk<SourceItem>(relaxed = true)
        coEvery { contextAgent.analyzeSource(source) } returns Unit
        coEvery { eventAgent.extractDeliverables(source) } returns Unit
        coEvery { eventAgent.pushToCalendar() } returns emptyList()
        coEvery { eventAgent.generateStudyPlan(source) } returns Unit

        // Verify logging calls
    }
})
