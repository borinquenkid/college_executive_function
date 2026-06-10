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
        coEvery { sourceScanner.scanNewDriveFiles(any()) } returns emptyList()

        harness.runHarness(force = true)

        coVerify(exactly = 1) { sourceRepository.getAllSources() }
    }

    test("delegates watched directory management to source scanner") {
        val dirs = listOf("/path1", "/path2")
        coEvery { sourceScanner.getWatchedLocalDirectories() } returns dirs

        harness.getWatchedLocalDirectories() shouldBe dirs
    }

    test("delegates watched folder management to source scanner") {
        val folders = listOf("folder1", "folder2")
        coEvery { sourceScanner.getWatchedGDriveFolders() } returns folders

        harness.getWatchedGDriveFolders() shouldBe folders
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
