package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow

class SourceProcessingPipelineTest : FunSpec({

    val source = mockk<SourceItem>(relaxed = true).also {
        every { it.id } returns "test-source-id"
    }

    // A relaxed EventAgent whose date-resolution gate is open (no doubts) by default.
    fun mockEventAgent(): EventAgent = mockk<EventAgent>(relaxed = true).also {
        every { it.pendingDateResolutions } returns MutableStateFlow(emptyList())
    }

    fun pipeline(
        eventAgent: EventAgent = mockEventAgent(),
        contextAgent: ContextAgent = mockk(relaxed = true),
        bugReporter: BugReporter? = null,
        sourceRepository: SourceRepository? = null,
        logger: Logger = mockk(relaxed = true)
    ) = SourceProcessingPipeline(
        ingestionAgent = mockk(relaxed = true),
        eventAgent = eventAgent,
        contextAgent = contextAgent,
        logger = logger,
        bugReporter = bugReporter,
        sourceRepository = sourceRepository
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

    // ---- Phase markers (ADR 0012) -----------------------------------------------

    test("processSource reports phases once, in order, ending DONE") {
        val sourceRepository = mockk<SourceRepository>(relaxed = true)

        pipeline(sourceRepository = sourceRepository).processSource(source)

        coVerifyOrder {
            sourceRepository.updateSourceStatus("test-source-id", SourceStatus.ANALYZING_CONTEXT)
            sourceRepository.updateSourceStatus("test-source-id", SourceStatus.EXTRACTING_DELIVERABLES)
            sourceRepository.updateSourceStatus("test-source-id", SourceStatus.RESOLVING_CONFLICTS)
            sourceRepository.updateSourceStatus("test-source-id", SourceStatus.DONE)
        }
        coVerify(exactly = 4) { sourceRepository.updateSourceStatus(any(), any()) }
    }

    test("processSource reports FAILED, not DONE, when a step throws") {
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val contextAgent = mockk<ContextAgent>()
        coEvery { contextAgent.analyzeSource(source) } throws Exception("Analysis failed")

        shouldThrow<Exception> {
            pipeline(contextAgent = contextAgent, sourceRepository = sourceRepository).processSource(source)
        }

        coVerify(exactly = 1) { sourceRepository.updateSourceStatus("test-source-id", SourceStatus.FAILED) }
        coVerify(exactly = 0) { sourceRepository.updateSourceStatus("test-source-id", SourceStatus.DONE) }
    }

    test("processSource works with no sourceRepository (other platforms don't need it)") {
        pipeline(sourceRepository = null).processSource(source)
        // No assertion beyond "doesn't throw" — sourceRepository is optional (Android/iOS/Desktop).
    }

    // ---- Swallowed chunk failures (analyzeSource/extractDeliverables never throw) --------------

    test("a chunk AI-call failure (returns false, doesn't throw) still reaches DONE but is logged") {
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val eventAgent = mockEventAgent()
        // Simulates the real EventAgent/ContextAgent contract: an AI-call failure is caught
        // internally and reported as `false`, never as a thrown exception (see runAgentAction).
        coEvery { contextAgent.analyzeSource(source) } returns false
        coEvery { eventAgent.extractDeliverables(source) } returns true

        pipeline(eventAgent, contextAgent, sourceRepository = sourceRepository, logger = logger)
            .processSource(source)

        // Success per product decision: every chunk was at least attempted, so this is DONE, not
        // FAILED — an AI call erroring is not "nothing useful found", but it's still not fatal.
        coVerify(exactly = 1) { sourceRepository.updateSourceStatus("test-source-id", SourceStatus.DONE) }
        coVerify(exactly = 0) { sourceRepository.updateSourceStatus("test-source-id", SourceStatus.FAILED) }
        // But it must be traceable regardless of what the UI shows.
        verify(exactly = 1) { logger.e(any(), match { it.contains("Chunk failure") && it.contains("contextOk=false") }) }
    }

    test("no chunk-failure log when both steps report success") {
        val sourceRepository = mockk<SourceRepository>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val contextAgent = mockk<ContextAgent>(relaxed = true)
        val eventAgent = mockEventAgent()
        coEvery { contextAgent.analyzeSource(source) } returns true
        coEvery { eventAgent.extractDeliverables(source) } returns true

        pipeline(eventAgent, contextAgent, sourceRepository = sourceRepository, logger = logger)
            .processSource(source)

        verify(exactly = 0) { logger.e(any(), match { it.contains("Chunk failure") }) }
    }
})
