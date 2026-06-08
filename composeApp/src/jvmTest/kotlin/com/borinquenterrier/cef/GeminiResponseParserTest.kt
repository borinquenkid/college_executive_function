package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class GeminiResponseParserTest : FunSpec({

    test("parseEventsJson parses a DayEvent and a TimeEvent from a bare array") {
        val response = """
            [
              {"title": "Reading Response", "type": "DAY", "date": "2026-06-02", "category": "DEADLINE"},
              {"title": "Lecture", "type": "TIME", "date": "2026-06-03", "startTime": "10:00", "endTime": "11:30", "category": "CLASS"}
            ]
        """.trimIndent()

        val parsed = GeminiResponseParser.parseEventsJson(response)

        parsed.size shouldBe 2
        parsed[0].title shouldBe "Reading Response"
        parsed[0].date shouldBe LocalDate(2026, 6, 2)
        parsed[0].category shouldBe AcademicCategory.DEADLINE
        val timeEvent = parsed[1] as TimeEvent
        timeEvent.startTime shouldBe LocalTime(10, 0)
        timeEvent.endTime shouldBe LocalTime(11, 30)
    }

    test("parseEventsJson extracts an array nested under an 'events' key and strips code fences") {
        val response = "```json\n{\"events\": [{\"title\": \"Midterm\", \"type\": \"DAY\", \"date\": \"2026-07-01\", \"category\": \"FINALS\"}]}\n```"

        val parsed = GeminiResponseParser.parseEventsJson(response)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Midterm"
        parsed[0].category shouldBe AcademicCategory.FINALS
    }

    test("parseEventsJson coerces explicit nulls and missing fields to their DTO defaults") {
        val response = """[{"title": null, "type": "DAY", "date": null, "category": "BOGUS_CATEGORY"}]"""

        val parsed = GeminiResponseParser.parseEventsJson(response)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Untitled Event"
        parsed[0].date shouldBe LocalDate(2024, 1, 1)
        parsed[0].category shouldBe AcademicCategory.REGULAR
    }

    test("parseEventsJson leniently coerces a numeric-string gradeWeight and falls back to default times on bad input") {
        val response = """[{"title": "Final Exam", "type": "TIME", "date": "2026-08-01", "gradeWeight": "0.4", "startTime": "noon", "endTime": "later"}]"""

        val parsed = GeminiResponseParser.parseEventsJson(response)

        val timeEvent = parsed[0] as TimeEvent
        timeEvent.gradeWeight shouldBe 0.4f
        timeEvent.startTime shouldBe LocalTime(9, 0)
        timeEvent.endTime shouldBe LocalTime(10, 0)
    }

    test("parseEventsJson throws when the JSON shape is neither an array nor an 'events' object") {
        shouldThrow<Exception> {
            GeminiResponseParser.parseEventsJson("""{"title": "Not a list"}""")
        }
    }

    test("parseDecomposeTaskJson coerces a numeric-string daysBeforeDue and extracts an array nested under 'tasks'") {
        val response = """{"tasks": [{"title": "Outline", "daysBeforeDue": "3", "description": "Draft an outline"}]}"""

        val parsed = GeminiResponseParser.parseDecomposeTaskJson(response)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Outline"
        parsed[0].daysBeforeDue shouldBe 3
        parsed[0].description shouldBe "Draft an outline"
    }

    test("parseDecomposeTaskJson falls back to defaults for missing fields") {
        val response = """[{}]"""

        val parsed = GeminiResponseParser.parseDecomposeTaskJson(response)

        parsed[0].title shouldBe "Sub-task"
        parsed[0].daysBeforeDue shouldBe 1
        parsed[0].description shouldBe ""
    }

    test("parseCategorizeSourceJson maps known category strings case-insensitively, including spaced and underscored variants") {
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "syllabus"}""") shouldBe SourceCategory.SYLLABUS
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "READING MATERIAL"}""") shouldBe SourceCategory.READING_MATERIAL
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "lab_manual"}""") shouldBe SourceCategory.LAB_MANUAL
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "Lecture Notes"}""") shouldBe SourceCategory.LECTURE_NOTES
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "unknown"}""") shouldBe SourceCategory.OTHER
    }

    test("parseCategorizeSourceJson defaults to OTHER when the category field is missing") {
        GeminiResponseParser.parseCategorizeSourceJson("""{}""") shouldBe SourceCategory.OTHER
    }

    test("extractSourceYears finds 4-digit years starting with 20 in free text") {
        GeminiResponseParser.extractSourceYears("Spring 2026 syllabus, revised from Fall 2025") shouldBe setOf(2026, 2025)
        GeminiResponseParser.extractSourceYears("No years here") shouldBe emptySet()
    }

    test("filterToSourceYears keeps only events whose year is in the source years, or all events when the set is empty") {
        val events = listOf(
            DayEvent(title = "A", source = EventSource.AI_GENERATED, date = LocalDate(2026, 1, 1)),
            DayEvent(title = "B", source = EventSource.AI_GENERATED, date = LocalDate(2030, 1, 1))
        )

        GeminiResponseParser.filterToSourceYears(events, setOf(2026)).map { it.title } shouldBe listOf("A")
        GeminiResponseParser.filterToSourceYears(events, emptySet()) shouldBe events
    }
})
