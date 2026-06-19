package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk

class SourceProcessingPipelineTest : FunSpec({

    val source = mockk<SourceItem>(relaxed = true)

    fun pipeline(
        eventAgent: EventAgent = mockk(relaxed = true),
        contextAgent: ContextAgent = mockk(relaxed = true),
        bugReporter: BugReporter? = null
    ) = SourceProcessingPipeline(
        ingestionAgent = mockk(relaxed = true),
        eventAgent = eventAgent,
        contextAgent = contextAgent,
        logger = mockk(relaxed = true),
        bugReporter = bugReporter
    )

    test("processSource calls steps in correct order including autoDecomposeDeliverables") {
        val eventAgent = mockk<EventAgent>(relaxed = true)
        val contextAgent = mockk<ContextAgent>(relaxed = true)

        pipeline(eventAgent, contextAgent).processSource(source)

        coVerifyOrder {
            contextAgent.analyzeSource(source)
            eventAgent.extractDeliverables(source)
            eventAgent.pushToCalendar()
            eventAgent.autoDecomposeDeliverables()
            eventAgent.generateStudyPlan(source)
            eventAgent.pushToCalendar()
        }
    }

    test("autoDecomposeDeliverables is called exactly once between the two push calls") {
        val eventAgent = mockk<EventAgent>(relaxed = true)

        pipeline(eventAgent).processSource(source)

        coVerify(exactly = 1) { eventAgent.autoDecomposeDeliverables() }
        coVerify(exactly = 2) { eventAgent.pushToCalendar() }
    }

    test("processSource rethrows exception and reports to bugReporter") {
        val bugReporter = mockk<BugReporter>(relaxed = true)
        val contextAgent = mockk<ContextAgent>()
        coEvery { contextAgent.analyzeSource(source) } throws Exception("Analysis failed")

        shouldThrow<Exception> {
            pipeline(contextAgent = contextAgent, bugReporter = bugReporter).processSource(source)
        }

        coVerify { bugReporter.reportError(any(), any()) }
    }

    test("processSource rethrows even without bugReporter") {
        val contextAgent = mockk<ContextAgent>()
        coEvery { contextAgent.analyzeSource(source) } throws Exception("No reporter")

        shouldThrow<Exception> {
            pipeline(contextAgent = contextAgent).processSource(source)
        }
    }
})
