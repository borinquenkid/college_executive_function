package com.borinquenterrier.cef

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.seconds

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

    test("processSource calls steps in correct order including autoDecomposeDeliverables") {
        val eventAgent = mockEventAgent()
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
        val eventAgent = mockEventAgent()

        pipeline(eventAgent).processSource(source)

        coVerify(exactly = 1) { eventAgent.autoDecomposeDeliverables() }
        coVerify(exactly = 2) { eventAgent.pushToCalendar() }
    }

    test("processSource pauses before the final push until pending date resolutions are cleared") {
        // The study plan flagged one deliverable with an ungrounded date.
        val flagged = DateResolutionItem(
            DayEvent(title = "Phantom", source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE, date = LocalDate(2026, 1, 1)),
            sourceSnippet = null
        )
        val pending = MutableStateFlow(listOf(flagged))
        val eventAgent = mockk<EventAgent>(relaxed = true)
        every { eventAgent.pendingDateResolutions } returns pending

        val job = CoroutineScope(Dispatchers.Default).launch {
            pipeline(eventAgent).processSource(source)
        }

        // It reaches the gate (first push done) but blocks before the second push.
        eventually(3.seconds) { coVerify(exactly = 1) { eventAgent.pushToCalendar() } }
        coVerify(exactly = 1) { eventAgent.pushToCalendar() } // still parked at the gate

        // User resolves/discards the doubt → the pipeline resumes itself and pushes.
        pending.value = emptyList()
        job.join()
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
