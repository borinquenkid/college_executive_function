package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class AcademicCalendarLogicTest : FunSpec({

    test("getSemesterRange returns Fall semester (Aug 1 - Dec 31)") {
        val today1 = LocalDate(2026, 8, 15)
        val today2 = LocalDate(2026, 12, 1)

        val range1 = AcademicCalendarLogic.getSemesterRange(today1)
        val range2 = AcademicCalendarLogic.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31))
        range2 shouldBe (LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31))
    }

    test("getSemesterRange returns Spring semester (Jan 1 - May 31)") {
        val today1 = LocalDate(2026, 1, 15)
        val today2 = LocalDate(2026, 5, 20)

        val range1 = AcademicCalendarLogic.getSemesterRange(today1)
        val range2 = AcademicCalendarLogic.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 1, 1) to LocalDate(2026, 5, 31))
        range2 shouldBe (LocalDate(2026, 1, 1) to LocalDate(2026, 5, 31))
    }

    test("getSemesterRange returns Interim/Summer semester (today - today + 30 days)") {
        val today = LocalDate(2026, 6, 15)
        val range = AcademicCalendarLogic.getSemesterRange(today)

        range shouldBe (LocalDate(2026, 6, 15) to LocalDate(2026, 7, 15))
    }

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

        val result = AcademicCalendarLogic.getExpandedAndFilteredEvents(
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
