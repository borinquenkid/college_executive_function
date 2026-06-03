package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ConfabulationGuardTest : FunSpec({

    fun dayEvent(year: Int, month: Int, day: Int) = DayEvent(
        title = "Event $year-$month-$day",
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.REGULAR,
        date = LocalDate(year, month, day)
    )

    fun timeEvent(year: Int, month: Int, day: Int) = TimeEvent(
        title = "Event $year-$month-$day",
        source = EventSource.AI_GENERATED,
        category = AcademicCategory.REGULAR,
        date = LocalDate(year, month, day),
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0)
    )

    // --- extractSourceYears ---

    test("extractSourceYears finds all 20xx years in text") {
        val years = GeminiAIService.extractSourceYears("Spring 2026 schedule. Last updated 2025.")
        years shouldBe setOf(2026, 2025)
    }

    test("extractSourceYears ignores non-20xx four-digit numbers") {
        val years = GeminiAIService.extractSourceYears("Page 1234. Course code 1101. See section 3000.")
        years.shouldBeEmpty()
    }

    test("extractSourceYears returns empty set when no years present") {
        GeminiAIService.extractSourceYears("No dates here at all.").shouldBeEmpty()
    }

    test("extractSourceYears deduplicates repeated years") {
        val years = GeminiAIService.extractSourceYears("2026 midterm, 2026 final, 2026 holiday")
        years shouldBe setOf(2026)
    }

    // --- filterToSourceYears ---

    test("filterToSourceYears keeps events in source years") {
        val events = listOf(dayEvent(2026, 1, 19), dayEvent(2026, 5, 4))
        GeminiAIService.filterToSourceYears(events, setOf(2026)) shouldHaveSize 2
    }

    test("filterToSourceYears drops events outside source years") {
        val events = listOf(dayEvent(2026, 5, 4), dayEvent(2024, 11, 15), dayEvent(2024, 12, 25))
        val filtered = GeminiAIService.filterToSourceYears(events, setOf(2026))
        filtered shouldHaveSize 1
        (filtered[0] as DayEvent).date.year shouldBe 2026
    }

    test("filterToSourceYears works for TimeEvent as well as DayEvent") {
        val events = listOf(timeEvent(2026, 2, 9), timeEvent(2024, 9, 3))
        val filtered = GeminiAIService.filterToSourceYears(events, setOf(2026))
        filtered shouldHaveSize 1
        (filtered[0] as TimeEvent).date.year shouldBe 2026
    }

    test("filterToSourceYears is a no-op when sourceYears is empty") {
        val events = listOf(dayEvent(2024, 1, 1), dayEvent(2025, 6, 15))
        GeminiAIService.filterToSourceYears(events, emptySet()) shouldHaveSize 2
    }

    test("filterToSourceYears allows multiple source years") {
        val events = listOf(dayEvent(2025, 12, 31), dayEvent(2026, 1, 1), dayEvent(2024, 6, 1))
        val filtered = GeminiAIService.filterToSourceYears(events, setOf(2025, 2026))
        filtered shouldHaveSize 2
    }

    test("parseEventsJson should successfully extract gradeWeight from JSON response") {
        val jsonPayload = """
            [
              {
                "title": "Midterm Exam",
                "type": "DAY",
                "category": "FINALS",
                "date": "2026-10-14",
                "gradeWeight": 0.15
              },
              {
                "title": "Final Project",
                "type": "TIME",
                "category": "DEADLINE",
                "date": "2026-11-20",
                "startTime": "09:00",
                "endTime": "11:00",
                "gradeWeight": "0.25"
              },
              {
                "title": "Reading Assignment",
                "type": "DAY",
                "category": "REGULAR",
                "date": "2026-10-15"
              }
            ]
        """.trimIndent()

        val events = GeminiAIService.parseEventsJson(jsonPayload)
        events shouldHaveSize 3

        val midterm = events[0] as DayEvent
        midterm.title shouldBe "Midterm Exam"
        midterm.gradeWeight shouldBe 0.15f

        val project = events[1] as TimeEvent
        project.title shouldBe "Final Project"
        project.gradeWeight shouldBe 0.25f

        val reading = events[2] as DayEvent
        reading.title shouldBe "Reading Assignment"
        reading.gradeWeight shouldBe null
    }
})
