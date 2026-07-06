package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class AgentHarnessTest : FunSpec({

    lateinit var ingestionAgent: IngestionAgent
    lateinit var eventAgent: EventAgent
    lateinit var contextAgent: ContextAgent
    lateinit var calendarAgent: CalendarAgent
    lateinit var sourceRepository: SourceRepository
    lateinit var pollScheduler: PollScheduler
    lateinit var sourceScanner: SourceScanner
    lateinit var harnessSourceProcessor: HarnessSourceProcessor
    lateinit var userPreferenceMemoryRepository: UserPreferenceMemoryRepository
    lateinit var logger: Logger
    lateinit var harness: AgentHarness

    beforeEach {
        ingestionAgent = mockk(relaxed = true)
        eventAgent = mockk(relaxed = true)
        contextAgent = mockk(relaxed = true)
        calendarAgent = mockk(relaxed = true)
        sourceRepository = mockk(relaxed = true)
        pollScheduler = mockk(relaxed = true)
        sourceScanner = mockk(relaxed = true)
        harnessSourceProcessor = mockk(relaxed = true)
        userPreferenceMemoryRepository = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        harness = AgentHarness(
            ingestionAgent,
            eventAgent,
            contextAgent,
            calendarAgent,
            sourceRepository,
            pollScheduler,
            sourceScanner,
            harnessSourceProcessor,
            userPreferenceMemoryRepository,
            logger
        )
    }

    test("skips run if poll scheduler indicates not ready") {
        coEvery { pollScheduler.shouldPoll(false) } returns false

        harness.runHarness(force = false)

        coVerify(exactly = 0) { sourceRepository.getAllSources() }
    }

    test("runs if poll scheduler indicates ready") {
        coEvery { pollScheduler.shouldPoll(true) } returns true
        coEvery { sourceRepository.getAllSources() } returns emptyList()
        coEvery { sourceScanner.scanNewLocalFiles(any()) } returns emptyList()

        harness.runHarness(force = true)

        coVerify(exactly = 1) { sourceRepository.getAllSources() }
    }

    test("prunes old override logs on a successful run") {
        coEvery { pollScheduler.shouldPoll(true) } returns true
        coEvery { sourceRepository.getAllSources() } returns emptyList()
        coEvery { sourceScanner.scanNewLocalFiles(any()) } returns emptyList()

        harness.runHarness(force = true)

        coVerify(exactly = 1) { userPreferenceMemoryRepository.pruneOldLogs(any()) }
    }

    test("does not prune override logs when the poll is skipped") {
        coEvery { pollScheduler.shouldPoll(false) } returns false

        harness.runHarness(force = false)

        coVerify(exactly = 0) { userPreferenceMemoryRepository.pruneOldLogs(any()) }
    }

    test("auto-decomposes a small capped batch of deliverables on a successful run") {
        coEvery { pollScheduler.shouldPoll(true) } returns true
        coEvery { sourceRepository.getAllSources() } returns emptyList()
        coEvery { sourceScanner.scanNewLocalFiles(any()) } returns emptyList()

        harness.runHarness(force = true)

        coVerify(exactly = 1) { eventAgent.autoDecomposeDeliverables(calendarId = "default", maxDeliverables = 2) }
    }

    test("does not auto-decompose when the poll is skipped") {
        coEvery { pollScheduler.shouldPoll(false) } returns false

        harness.runHarness(force = false)

        coVerify(exactly = 0) { eventAgent.autoDecomposeDeliverables(calendarId = any(), maxDeliverables = any()) }
    }

    test("delegates watched directory management to source scanner") {
        val dirs = listOf("/path1", "/path2")
        coEvery { sourceScanner.getWatchedLocalDirectories() } returns dirs

        harness.getWatchedLocalDirectories() shouldBe dirs
    }

    test("delegates poll time to poll scheduler") {
        coEvery { pollScheduler.getLastPollTime() } returns 12345L

        harness.getLastPollTime() shouldBe 12345L
    }

    test("status starts as Idle") {
        harness.status.value shouldBe "Idle"
    }

    test("isBusy starts as false") {
        harness.isBusy.value shouldBe false
    }
})
