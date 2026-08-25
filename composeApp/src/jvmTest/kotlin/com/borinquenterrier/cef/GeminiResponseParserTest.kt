package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
        val response =
            "```json\n{\"events\": [{\"title\": \"Midterm\", \"type\": \"DAY\", \"date\": \"2026-07-01\", \"category\": \"FINALS\"}]}\n```"

        val parsed = GeminiResponseParser.parseEventsJson(response)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Midterm"
        parsed[0].category shouldBe AcademicCategory.FINALS
    }

    test("parseEventsJson coerces explicit nulls and missing fields to their DTO defaults") {
        val response =
            """[{"title": null, "type": "DAY", "date": null, "category": "BOGUS_CATEGORY"}]"""

        val parsed = GeminiResponseParser.parseEventsJson(response)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Untitled Event"
        parsed[0].date shouldBe LocalDate(2024, 1, 1)
        parsed[0].category shouldBe AcademicCategory.REGULAR
    }

    test("parseEventsJson coerces a numeric-string gradeWeight and downgrades a TIME event with an unparseable start to a DayEvent") {
        val response =
            """[{"title": "Final Exam", "type": "TIME", "date": "2026-08-01", "gradeWeight": "0.4", "startTime": "noon", "endTime": "later"}]"""

        val parsed = GeminiResponseParser.parseEventsJson(response)

        // No fabricated 09:00–10:00 — a student trusting an invented time is worse than
        // an all-day entry, so an unknown start means a date-only event.
        val event = parsed[0] as DayEvent
        event.title shouldBe "Final Exam"
        event.gradeWeight shouldBe 0.4f
        event.date shouldBe LocalDate(2026, 8, 1)
    }

    test("parseEventsJson downgrades a TIME event with no time fields at all to a DayEvent") {
        val response =
            """[{"title": "Quiz", "type": "TIME", "date": "2026-08-01", "category": "DEADLINE"}]"""

        val parsed = GeminiResponseParser.parseEventsJson(response)

        (parsed[0] is DayEvent) shouldBe true
        parsed[0].category shouldBe AcademicCategory.DEADLINE
    }

    test("parseEventsJson keeps a TIME event with a valid start and missing end, assuming 1h duration") {
        val response =
            """[{"title": "Lab", "type": "TIME", "date": "2026-08-01", "startTime": "14:00", "category": "CLASS"}]"""

        val parsed = GeminiResponseParser.parseEventsJson(response)

        val timeEvent = parsed[0] as TimeEvent
        timeEvent.startTime shouldBe LocalTime(14, 0)
        timeEvent.endTime shouldBe LocalTime(15, 0)
    }

    test("parseEventsJson throws when the JSON shape is neither an array nor an 'events' object") {
        shouldThrow<Exception> {
            GeminiResponseParser.parseEventsJson("""{"title": "Not a list"}""")
        }
    }

    test("parseDecomposeTaskJson coerces a numeric-string daysBeforeDue and extracts an array nested under 'tasks'") {
        val response =
            """{"tasks": [{"title": "Outline", "daysBeforeDue": "3", "description": "Draft an outline"}]}"""

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
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "calendar"}""") shouldBe SourceCategory.CALENDAR
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "READING MATERIAL"}""") shouldBe SourceCategory.READING_MATERIAL
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "lab_manual"}""") shouldBe SourceCategory.LAB_MANUAL
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "Lecture Notes"}""") shouldBe SourceCategory.LECTURE_NOTES
        GeminiResponseParser.parseCategorizeSourceJson("""{"category": "unknown"}""") shouldBe SourceCategory.OTHER
    }

    test("parseCategorizeSourceJson defaults to OTHER when the category field is missing") {
        GeminiResponseParser.parseCategorizeSourceJson("""{}""") shouldBe SourceCategory.OTHER
    }

    test("parseCategorizeSourceJson throws SourceValidationException when isValid is false") {
        val json =
            """{"category": "syllabus", "isValid": false, "reason": "No assignments or meeting times found."}"""
        val ex = shouldThrow<SourceValidationException> {
            GeminiResponseParser.parseCategorizeSourceJson(json)
        }
        ex.message shouldBe "No assignments or meeting times found."
    }

    test("extractSourceYears finds 4-digit years starting with 20 in free text") {
        GeminiResponseParser.extractSourceYears("Spring 2026 syllabus, revised from Fall 2025") shouldBe setOf(
            2026,
            2025
        )
        GeminiResponseParser.extractSourceYears("No years here") shouldBe emptySet()
    }

    test("filterToSourceYears keeps only events whose year is in the source years, or all events when the set is empty") {
        val events = listOf(
            DayEvent(title = "A", source = EventSource.AI_GENERATED, date = LocalDate(2026, 1, 1)),
            DayEvent(title = "B", source = EventSource.AI_GENERATED, date = LocalDate(2030, 1, 1))
        )

        GeminiResponseParser.filterToSourceYears(events, setOf(2026))
            .map { it.title } shouldBe listOf("A")
        GeminiResponseParser.filterToSourceYears(events, emptySet()) shouldBe events
    }

    // --- remapOffByOneYears (HIS 378W fall-2025 batch-2 regression, 2026-08-25) ---

    test("remapOffByOneYears moves an event one year ahead of the source term back onto it") {
        val events = listOf(
            DayEvent(title = "Women in Late Qing China", source = EventSource.AI_GENERATED,
                date = LocalDate(2026, 9, 30), category = AcademicCategory.CLASS)
        )
        val remapped = GeminiResponseParser.remapOffByOneYears(events, setOf(2025))
        remapped.single().date shouldBe LocalDate(2025, 9, 30)
    }

    test("remapOffByOneYears moves an event one year behind the source term forward onto it") {
        val events = listOf(
            DayEvent(title = "First class", source = EventSource.AI_GENERATED, date = LocalDate(2026, 1, 15))
        )
        GeminiResponseParser.remapOffByOneYears(events, setOf(2027))
            .single().date shouldBe LocalDate(2027, 1, 15)
    }

    test("remapOffByOneYears leaves grounded, far-off, and ambiguous events unchanged") {
        val grounded = DayEvent(title = "G", source = EventSource.AI_GENERATED, date = LocalDate(2025, 9, 1))
        val farOff = DayEvent(title = "F", source = EventSource.AI_GENERATED, date = LocalDate(2099, 9, 1))
        // 2026 sits one year from BOTH 2025 and 2027 — ambiguous, so left for the filter to drop.
        val ambiguous = DayEvent(title = "X", source = EventSource.AI_GENERATED, date = LocalDate(2026, 9, 1))
        GeminiResponseParser.remapOffByOneYears(
            listOf(grounded, farOff, ambiguous), setOf(2025, 2027)
        ) shouldBe listOf(grounded, farOff, ambiguous)
    }

    test("remapOffByOneYears with an empty source-year set is a no-op") {
        val events = listOf(
            DayEvent(title = "A", source = EventSource.AI_GENERATED, date = LocalDate(2026, 9, 1))
        )
        GeminiResponseParser.remapOffByOneYears(events, emptySet()) shouldBe events
    }

    test("remapOffByOneYears drops nothing itself and skips a calendar-invalid swap (Feb 29)") {
        val leap = DayEvent(title = "Leap", source = EventSource.AI_GENERATED, date = LocalDate(2024, 2, 29))
        val result = GeminiResponseParser.remapOffByOneYears(listOf(leap), setOf(2025))
        // 2025-02-29 doesn't exist; the event passes through unchanged for the year filter.
        result shouldBe listOf(leap)
    }

    test("remapOffByOneYears preserves TimeEvent times while swapping the year") {
        val timed = TimeEvent(id = null, title = "Seminar", source = EventSource.AI_GENERATED,
            startTime = LocalTime(15, 30), endTime = LocalTime(17, 0),
            date = LocalDate(2026, 10, 21), category = AcademicCategory.CLASS)
        val result = GeminiResponseParser.remapOffByOneYears(listOf(timed), setOf(2025)).single() as TimeEvent
        result.date shouldBe LocalDate(2025, 10, 21)
        result.startTime shouldBe LocalTime(15, 30)
        result.endTime shouldBe LocalTime(17, 0)
    }

    // --- midnight overflow (Bug 8B) ---

    test("TIME event at 23:59 with omitted endTime overflows midnight → DayEvent") {
        val response = """[{
            "title": "Late Night Alarm",
            "type": "TIME",
            "date": "2026-09-01",
            "startTime": "23:59",
            "category": "DEADLINE"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response).first()
        (event is DayEvent) shouldBe true
        event.title shouldBe "Late Night Alarm"
    }

    test("TIME event at 23:30 with endTime before start overflows midnight → DayEvent") {
        val response = """[{
            "title": "Midnight Session",
            "type": "TIME",
            "date": "2026-09-01",
            "startTime": "23:30",
            "endTime": "22:00",
            "category": "STUDY_BLOCK"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response).first()
        (event is DayEvent) shouldBe true
    }

    test("TIME event at 22:30 with invalid endTime adds 1h without overflow → TimeEvent(endTime=23:30)") {
        val response = """[{
            "title": "Evening Review",
            "type": "TIME",
            "date": "2026-09-01",
            "startTime": "22:30",
            "endTime": "21:00",
            "category": "STUDY_BLOCK"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response).first() as TimeEvent
        event.endTime shouldBe LocalTime(23, 30)
    }

    // ── week-anchor date grounding ────────────────────────────────────────────

    val week6Anchors = mapOf(6 to LocalDate(2026, 7, 13))

    test("parseEventsJson grounds a week-derived date to the anchor table and flags the correction") {
        // 2026-08-16 eval regression: model computed Week 6 Wednesday as Jul 16 (truth Jul 15).
        val response = """[{
            "title": "Issue Brief #2 due",
            "type": "DAY",
            "date": "2026-07-16",
            "category": "DEADLINE",
            "weekNumber": 6,
            "dayName": "WEDNESDAY"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response, weekAnchors = week6Anchors).first()
        event.date shouldBe LocalDate(2026, 7, 15)
        event.warning!! shouldContain "Week 6"
    }

    test("parseEventsJson appends the grounding note to an existing model warning") {
        val response = """[{
            "title": "Issue Brief #2 due",
            "type": "DAY",
            "date": "2026-07-16",
            "category": "DEADLINE",
            "warning": "Date calculated from week number",
            "weekNumber": 6,
            "dayName": "WEDNESDAY"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response, weekAnchors = week6Anchors).first()
        event.warning!! shouldContain "Date calculated from week number"
        event.warning!! shouldContain "Week 6"
    }

    test("parseEventsJson leaves date and warning untouched when the model date already matches the grid") {
        val response = """[{
            "title": "Revision Interviews",
            "type": "DAY",
            "date": "2026-07-13",
            "category": "CLASS",
            "weekNumber": 6,
            "dayName": "MONDAY"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response, weekAnchors = week6Anchors).first()
        event.date shouldBe LocalDate(2026, 7, 13)
        event.warning shouldBe null
    }

    test("parseEventsJson keeps the model date when no anchor table is supplied") {
        val response = """[{
            "title": "Issue Brief #2 due",
            "type": "DAY",
            "date": "2026-07-16",
            "category": "DEADLINE",
            "weekNumber": 6,
            "dayName": "WEDNESDAY"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response).first()
        event.date shouldBe LocalDate(2026, 7, 16)
    }

    test("parseEventsJson grounds a TimeEvent's date while preserving its clock times") {
        val response = """[{
            "title": "Issue Brief #2 due",
            "type": "TIME",
            "date": "2026-07-16",
            "startTime": "14:00",
            "endTime": "15:15",
            "category": "DEADLINE",
            "weekNumber": 6,
            "dayName": "WEDNESDAY"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response, weekAnchors = week6Anchors).first() as TimeEvent
        event.date shouldBe LocalDate(2026, 7, 15)
        event.startTime shouldBe LocalTime(14, 0)
        event.endTime shouldBe LocalTime(15, 15)
    }

    test("parseEventsJson tolerates weekNumber emitted as a JSON string") {
        val response = """[{
            "title": "Issue Brief #2 due",
            "type": "DAY",
            "date": "2026-07-16",
            "category": "DEADLINE",
            "weekNumber": "6",
            "dayName": "WEDNESDAY"
        }]"""
        val event = GeminiResponseParser.parseEventsJson(response, weekAnchors = week6Anchors).first()
        event.date shouldBe LocalDate(2026, 7, 15)
    }

    test("parseEventsJson without the new fields behaves exactly as before (backward compat)") {
        val response = """[{"title": "Midterm", "type": "DAY", "date": "2026-07-01", "category": "FINALS"}]"""
        val event = GeminiResponseParser.parseEventsJson(response, weekAnchors = week6Anchors).first()
        event.date shouldBe LocalDate(2026, 7, 1)
        event.warning shouldBe null
    }
})
