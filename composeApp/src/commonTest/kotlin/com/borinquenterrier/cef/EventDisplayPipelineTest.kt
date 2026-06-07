package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class EventDisplayPipelineTest : FunSpec({

    test("getExpandedAndFilteredEvents filters and sorts events correctly within range") {
        val startDate = LocalDate(2026, 6, 10)
        val endDate = LocalDate(2026, 6, 20)

        // Event before range
        val eventBefore = DayEvent(
            id = "before",
            title = "Before Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 5)
        )

        // Event after range
        val eventAfter = DayEvent(
            id = "after",
            title = "After Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 25)
        )

        // Events inside range (out of order to test sorting)
        val eventInsideLate = DayEvent(
            id = "inside-late",
            title = "Inside Late Event",
            source = EventSource.STUDENT,
            date = LocalDate(2026, 6, 18)
        )

        val eventInsideEarly = TimeEvent(
            id = "inside-early",
            title = "Inside Early Event",
            source = EventSource.STUDENT,
            date = LocalDate(2026, 6, 12),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0)
        )

        // List of remote/displayed events
        val displayedEvents = listOf(eventBefore, eventAfter, eventInsideLate, eventInsideEarly)

        val result = EventDisplayPipeline.getExpandedAndFilteredEvents(
            routineEvents = emptyList(),
            aiGeneratedEvents = emptyList(),
            displayedEvents = displayedEvents,
            startDate = startDate,
            endDate = endDate,
            timeZone = TimeZone.UTC
        )

        result.size shouldBe 2
        result[0].id shouldBe "inside-early"
        result[1].id shouldBe "inside-late"
    }
})
