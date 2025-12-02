package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventGeneratorTest : FunSpec({

    // Test data
    val startTime = LocalTime(9, 0)
    val endTime = LocalTime(10, 0)
    val semesterStart = LocalDate(2024, 1, 8)
    val semesterEnd = LocalDate(2024, 5, 3)

    val mondayClass = TimeEvent(
        title = "CS 101",
        source = EventSource.ROUTINE,
        startTime = startTime,
        endTime = endTime,
        date = semesterStart, // Base date, recurrence rules will be used
        recurrence = Recurrence(
            daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            startDate = semesterStart,
            endDate = semesterEnd
        )
    )

    test("expandEvents should generate correct number of instances for a recurring event") {
        // 1. Arrange: A two-week view in the middle of the semester
        val viewStart = LocalDate(2024, 2, 5) // A Monday
        val viewEnd = LocalDate(2024, 2, 18)   // Two weeks later, a Sunday

        // 2. Act
        val events = listOf(mondayClass)
        val expandedEvents = EventGenerator.expandEvents(events, viewStart, viewEnd)

        // 3. Assert: Should be 4 instances (2 Mondays, 2 Wednesdays)
        expandedEvents shouldHaveSize 4
        (expandedEvents[0] as TimeEvent).date shouldBe LocalDate(2024, 2, 5) // Mon
        (expandedEvents[1] as TimeEvent).date shouldBe LocalDate(2024, 2, 7) // Wed
        (expandedEvents[2] as TimeEvent).date shouldBe LocalDate(2024, 2, 12) // Next Mon
        (expandedEvents[3] as TimeEvent).date shouldBe LocalDate(2024, 2, 14) // Next Wed
    }

    test("expandEvents should not generate events outside the recurrence range") {
        // 1. Arrange: A view range that is before the event's recurrence starts
        val viewStart = LocalDate(2023, 12, 1)
        val viewEnd = LocalDate(2023, 12, 31)

        // 2. Act
        val events = listOf(mondayClass)
        val expandedEvents = EventGenerator.expandEvents(events, viewStart, viewEnd)

        // 3. Assert
        expandedEvents.isEmpty() shouldBe true
    }

    test("expandEvents should handle non-recurring events correctly") {
        // 1. Arrange
        val singleEvent = TimeEvent(
            title = "Special Lecture",
            source = EventSource.MANUAL,
            startTime = startTime, endTime = endTime,
            date = LocalDate(2024, 3, 15), // A specific Friday
            recurrence = null
        )
        val viewStart = LocalDate(2024, 3, 1)
        val viewEnd = LocalDate(2024, 3, 31)

        // 2. Act
        val events = listOf(singleEvent)
        val expandedEvents = EventGenerator.expandEvents(events, viewStart, viewEnd)

        // 3. Assert
        expandedEvents shouldHaveSize 1
        (expandedEvents[0] as TimeEvent).date shouldBe LocalDate(2024, 3, 15)
    }
})
