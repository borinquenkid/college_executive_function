package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class IcsStringBuilderTest : FunSpec({

    test("buildIcsString generates valid iCalendar string for TimeEvent") {
        val event = TimeEvent(
            id = "test-event-1",
            title = "Lecture 1",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(9, 30),
            endTime = LocalTime(11, 0)
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        ics shouldContain "BEGIN:VCALENDAR"
        ics shouldContain "VERSION:2.0"
        ics shouldContain "BEGIN:VEVENT"
        ics shouldContain "UID:test-event-1"
        ics shouldContain "SUMMARY:Lecture 1"
        ics shouldContain "DTSTART:20260610T093000"
        ics shouldContain "DTEND:20260610T110000"
        ics shouldContain "DESCRIPTION:Source: MANUAL\\nCategory: REGULAR"
        ics shouldContain "END:VEVENT"
        ics shouldContain "END:VCALENDAR"
    }

    test("buildIcsString generates valid iCalendar string for DayEvent") {
        val event = DayEvent(
            id = "test-event-2",
            title = "Holiday",
            source = EventSource.SCHOOL,
            date = LocalDate(2026, 6, 12)
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        ics shouldContain "BEGIN:VCALENDAR"
        ics shouldContain "BEGIN:VEVENT"
        ics shouldContain "UID:test-event-2"
        ics shouldContain "SUMMARY:Holiday"
        ics shouldContain "DTSTART;VALUE=DATE:20260612"
        ics shouldContain "DTEND;VALUE=DATE:20260613"
        ics shouldContain "DESCRIPTION:Source: SCHOOL\\nCategory: REGULAR"
        ics shouldContain "END:VEVENT"
    }
})
