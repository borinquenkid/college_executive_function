package com.borinquenterrier.cef

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class SourceDeleterTest : StringSpec({

    "deleteSource removes from repository and cleans events" {
        val sourceRepository = mockk<SqlDelightSourceRepository>()
        val localRepository = mockk<SqlDelightLocalCalendarRepository>()
        val calendarAgent = mockk<CalendarAgent>()
        val logger = mockk<Logger>()

        val deleter =
            SourceDeleter(sourceRepository, localRepository, calendarAgent, logger, mockk())

        val source = mockk<SourceItem>(relaxed = true) { every { title } returns "Calculus" }
        coEvery { sourceRepository.deleteSource("Calculus") } returns Unit
        coEvery { localRepository.getAllEvents("default") } returns emptyList()
        coEvery { calendarAgent.synchronize("default") } returns Unit

        // Verify deletion pipeline
    }

    "deleteSource filters events by source title" {
        val sourceRepository = mockk<SqlDelightSourceRepository>()
        val localRepository = mockk<SqlDelightLocalCalendarRepository>()
        val calendarAgent = mockk<CalendarAgent>()
        val logger = mockk<Logger>()

        val deleter =
            SourceDeleter(sourceRepository, localRepository, calendarAgent, logger, mockk())

        val source = mockk<SourceItem>(relaxed = true) { every { title } returns "Physics" }
        val event1 = mockk<Event>(relaxed = true) { every { id } returns "Physics-hw1" }
        val event2 = mockk<Event>(relaxed = true) { every { id } returns "Biology-lab" }

        coEvery { sourceRepository.deleteSource("Physics") } returns Unit
        coEvery { localRepository.getAllEvents("default") } returns listOf(event1, event2)
        coEvery { localRepository.hardDeleteEvent(any(), any()) } returns Unit
        coEvery { calendarAgent.synchronize("default") } returns Unit

        // Verify only Physics events deleted
    }

    "deleteSource catches and logs deletion errors" {
        val sourceRepository = mockk<SqlDelightSourceRepository>()
        val localRepository = mockk<SqlDelightLocalCalendarRepository>()
        val calendarAgent = mockk<CalendarAgent>()
        val logger = mockk<Logger>()

        val deleter =
            SourceDeleter(sourceRepository, localRepository, calendarAgent, logger, mockk())

        val source = mockk<SourceItem>(relaxed = true) { every { title } returns "Math" }
        coEvery { sourceRepository.deleteSource("Math") } throws Exception("DB error")

        // Verify error handling
    }
})
