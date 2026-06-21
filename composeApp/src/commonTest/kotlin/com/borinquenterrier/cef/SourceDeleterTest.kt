package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SourceDeleterTest : FunSpec({

    // Dispatchers.Unconfined runs scope.launch blocks immediately (synchronously)
    // in the calling coroutine, so assertions can follow deleteSource() directly.
    fun makeDeleter(
        sourceRepo: SqlDelightSourceRepository,
        calendarAgent: CalendarAgent,
        logger: Logger
    ) = SourceDeleter(sourceRepo, calendarAgent, logger, CoroutineScope(Dispatchers.Unconfined))

    test("deleteSource removes source and synchronizes after cleaning matching events") {
        val sourceRepo = mockk<SqlDelightSourceRepository>()
        val agent = mockk<CalendarAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem> { every { title } returns "Calculus" }

        coEvery { sourceRepo.deleteSource("Calculus") } returns Unit
        coEvery { agent.getEvents("default") } returns emptyList()

        makeDeleter(sourceRepo, agent, logger).deleteSource(source)

        coVerify(exactly = 1) { sourceRepo.deleteSource("Calculus") }
        coVerify(exactly = 1) { agent.synchronize("default") }
    }

    test("deleteSource deletes only events whose id starts with the source title") {
        val sourceRepo = mockk<SqlDelightSourceRepository>()
        val agent = mockk<CalendarAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem> { every { title } returns "Physics" }

        val physicsEvent = mockk<Event>(relaxed = true) {
            every { id } returns "Physics-hw1"
            every { warning } returns null
        }
        val biologyEvent = mockk<Event>(relaxed = true) {
            every { id } returns "Biology-lab"
            every { warning } returns null
        }

        coEvery { sourceRepo.deleteSource("Physics") } returns Unit
        coEvery { agent.getEvents("default") } returns listOf(physicsEvent, biologyEvent)

        makeDeleter(sourceRepo, agent, logger).deleteSource(source)

        coVerify(exactly = 1) { agent.deleteEvent("Physics-hw1", "default") }
        coVerify(exactly = 0) { agent.deleteEvent("Biology-lab", "default") }
    }

    test("deleteSource deletes events whose warning contains the source title") {
        val sourceRepo = mockk<SqlDelightSourceRepository>()
        val agent = mockk<CalendarAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem> { every { title } returns "Math" }

        val event = mockk<Event>(relaxed = true) {
            every { id } returns "other-id"
            every { warning } returns "Imported from Math syllabus"
        }

        coEvery { sourceRepo.deleteSource("Math") } returns Unit
        coEvery { agent.getEvents("default") } returns listOf(event)

        makeDeleter(sourceRepo, agent, logger).deleteSource(source)

        coVerify(exactly = 1) { agent.deleteEvent("other-id", "default") }
    }

    test("deleteSource skips events with null id") {
        val sourceRepo = mockk<SqlDelightSourceRepository>()
        val agent = mockk<CalendarAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem> { every { title } returns "Chem" }

        val event = mockk<Event>(relaxed = true) {
            every { id } returns null
            every { warning } returns null
        }

        coEvery { sourceRepo.deleteSource("Chem") } returns Unit
        coEvery { agent.getEvents("default") } returns listOf(event)

        makeDeleter(sourceRepo, agent, logger).deleteSource(source)

        coVerify(exactly = 0) { agent.deleteEvent(any(), any()) }
    }

    test("deleteSource catches and logs exceptions without propagating") {
        val sourceRepo = mockk<SqlDelightSourceRepository>()
        val agent = mockk<CalendarAgent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val source = mockk<SourceItem> { every { title } returns "Bio" }

        coEvery { sourceRepo.deleteSource("Bio") } throws RuntimeException("DB error")

        makeDeleter(sourceRepo, agent, logger).deleteSource(source)

        coVerify(exactly = 1) { logger.e(any(), any(), any()) }
        coVerify(exactly = 0) { agent.synchronize(any()) }
    }
})
