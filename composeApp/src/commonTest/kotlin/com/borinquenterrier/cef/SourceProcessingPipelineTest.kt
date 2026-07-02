package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

class SourceProcessingPipelineTest : FunSpec({

    val source = mockk<SourceItem>(relaxed = true)

    // A relaxed EventAgent whose date-resolution gate is open (no doubts) by default.
    fun mockEventAgent(): EventAgent = mockk<EventAgent>(relaxed = true).also {
        every { it.pendingDateResolutions } returns MutableStateFlow(emptyList())
    }

    fun pipeline(
        eventAgent: EventAgent = mockEventAgent(),
        contextAgent: ContextAgent = mockk(relaxed = true),
        bugReporter: BugReporter? = null
    ) = SourceProcessingPipeline(
        ingestionAgent = mockk(relaxed = true),
        eventAgent = eventAgent,
        contextAgent = contextAgent,
        logger = mockk(relaxed = true),
        bugReporter = bugReporter
    )

    test("processSource calls steps in correct order: context → extract → push") {
        val eventAgent = mockEventAgent()
        val contextAgent = mockk<ContextAgent>(relaxed = true)

        pipeline(eventAgent, contextAgent).processSource(source)

        coVerifyOrder {
            contextAgent.analyzeSource(source)
            eventAgent.extractDeliverables(source)
            eventAgent.pushToCalendar()
        }
    }

    test("processSource does NOT auto-run study plan or decomposition (both on-demand)") {
        val eventAgent = mockEventAgent()

        pipeline(eventAgent).processSource(source)

        coVerify(exactly = 0) { eventAgent.autoDecomposeDeliverables() }
        coVerify(exactly = 0) { eventAgent.generateStudyPlan(any()) }
        coVerify(exactly = 1) { eventAgent.pushToCalendar() }
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
