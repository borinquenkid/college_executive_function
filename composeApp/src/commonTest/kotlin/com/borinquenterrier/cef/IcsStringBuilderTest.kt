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
    test("buildIcsString includes warning in DESCRIPTION when event has a warning") {
        val event = TimeEvent(
            id = "warn-1",
            title = "Ambiguous Midterm",
            source = EventSource.AI_GENERATED,
            date = LocalDate(2026, 10, 14),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(12, 0),
            warning = "Date says Monday but Oct 14 is a Wednesday"
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        ics shouldContain "Warning: Date says Monday but Oct 14 is a Wednesday"
    }

    test("buildIcsString uses hash-based UID when event id is null") {
        val event = TimeEvent(
            id = null,
            title = "No-ID Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 3, 5),
            startTime = LocalTime(8, 0),
            endTime = LocalTime(9, 0)
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        // Expect "cef-<hash>-20260305" pattern — just confirm it doesn't say "null"
        ics shouldContain "UID:cef-"
    }

    test("buildIcsString produces minimal valid calendar for empty event list") {
        val ics = IcsStringBuilder.buildIcsString(emptyList())

        ics shouldContain "BEGIN:VCALENDAR"
        ics shouldContain "END:VCALENDAR"
        (ics.contains("BEGIN:VEVENT") shouldBe false)
    }

    test("buildIcsString includes RRULE for a recurring TimeEvent") {
        val recurrence = Recurrence(
            daysOfWeek = listOf(
                kotlinx.datetime.DayOfWeek.MONDAY,
                kotlinx.datetime.DayOfWeek.WEDNESDAY
            ),
            startDate = LocalDate(2026, 1, 5),
            endDate = LocalDate(2026, 5, 1)
        )
        val event = TimeEvent(
            id = "recurring-1",
            title = "CS 101",
            source = EventSource.ROUTINE,
            date = LocalDate(2026, 1, 5),
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0),
            recurrence = recurrence
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        ics shouldContain "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260501"
    }

    test("buildIcsString includes RRULE for a recurring DayEvent") {
        val recurrence = Recurrence(
            daysOfWeek = listOf(kotlinx.datetime.DayOfWeek.FRIDAY),
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 12, 20)
        )
        val event = DayEvent(
            id = "recurring-day-1",
            title = "Weekly Review",
            source = EventSource.ROUTINE,
            date = LocalDate(2026, 8, 7),
            recurrence = recurrence
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        ics shouldContain "RRULE:FREQ=WEEKLY;BYDAY=FR;UNTIL=20261220"
    }

    test("buildRecurrenceRule correctly maps all seven BYDAY abbreviations") {
        val allDays = listOf(
            kotlinx.datetime.DayOfWeek.MONDAY,
            kotlinx.datetime.DayOfWeek.TUESDAY,
            kotlinx.datetime.DayOfWeek.WEDNESDAY,
            kotlinx.datetime.DayOfWeek.THURSDAY,
            kotlinx.datetime.DayOfWeek.FRIDAY,
            kotlinx.datetime.DayOfWeek.SATURDAY,
            kotlinx.datetime.DayOfWeek.SUNDAY
        )
        val recurrence = Recurrence(
            daysOfWeek = allDays,
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 12, 31)
        )
        val event = TimeEvent(
            title = "All Days",
            source = EventSource.ROUTINE,
            date = LocalDate(2026, 1, 5),
            startTime = LocalTime(8, 0),
            endTime = LocalTime(9, 0),
            recurrence = recurrence
        )

        val ics = IcsStringBuilder.buildIcsString(listOf(event))

        ics shouldContain "BYDAY=MO,TU,WE,TH,FR,SA,SU"
    }

    test("DESCRIPTION omits Warning line when event has no warning") {
        val event = TimeEvent(
            id = "no-warn",
            title = "Regular Class",
            source = EventSource.ROUTINE,
            date = LocalDate(2026, 9, 7),
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 0),
            warning = null
        )
        val ics = IcsStringBuilder.buildIcsString(listOf(event))
        (ics.contains("Warning:") shouldBe false)
    }

    test("multiple events each produce their own VEVENT block") {
        val events = listOf(
            TimeEvent(
                id = "uid-1",
                title = "Lecture",
                source = EventSource.ROUTINE,
                date = LocalDate(2026, 9, 7),
                startTime = LocalTime(9, 0),
                endTime = LocalTime(10, 0)
            ),
            DayEvent(
                id = "uid-2",
                title = "Holiday",
                source = EventSource.SCHOOL,
                date = LocalDate(2026, 9, 8)
            )
        )
        val ics = IcsStringBuilder.buildIcsString(events)
        ics.split("BEGIN:VEVENT").size - 1 shouldBe 2
        ics shouldContain "SUMMARY:Lecture"
        ics shouldContain "SUMMARY:Holiday"
        ics.split("END:VEVENT").size - 1 shouldBe 2
    }

    test("no RRULE is emitted when recurrence is null") {
        val event = TimeEvent(
            id = "uid-no-recur",
            title = "One-off",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 9, 7),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            recurrence = null
        )
        val ics = IcsStringBuilder.buildIcsString(listOf(event))
        (ics.contains("RRULE:") shouldBe false)
    }

    test("time values are zero-padded to two digits") {
        val event = TimeEvent(
            id = "uid-pad",
            title = "Early Class",
            source = EventSource.ROUTINE,
            date = LocalDate(2026, 1, 5),
            startTime = LocalTime(8, 0),
            endTime = LocalTime(9, 0)
        )
        val ics = IcsStringBuilder.buildIcsString(listOf(event))
        ics shouldContain "DTSTART:20260105T080000"
        ics shouldContain "DTEND:20260105T090000"
    }
})
