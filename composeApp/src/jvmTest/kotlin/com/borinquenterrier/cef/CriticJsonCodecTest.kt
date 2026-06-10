package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class CriticJsonCodecTest : FunSpec({

    test("serializeEvents round-trips a DayEvent through parseEvents") {
        val events = listOf(
            DayEvent(
                title = "Reading Response",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.DEADLINE,
                date = LocalDate(2026, 6, 2)
            )
        )

        val json = CriticJsonCodec.serializeEvents(events)
        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Reading Response"
        parsed[0].date shouldBe LocalDate(2026, 6, 2)
        parsed[0].category shouldBe AcademicCategory.DEADLINE
    }

    test("serializeEvents round-trips a TimeEvent including start/end times") {
        val events = listOf(
            TimeEvent(
                title = "Lecture",
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.REGULAR,
                date = LocalDate(2026, 6, 3),
                startTime = LocalTime(9, 0),
                endTime = LocalTime(10, 30)
            )
        )

        val json = CriticJsonCodec.serializeEvents(events)
        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        val timeEvent = parsed[0] as TimeEvent
        timeEvent.startTime shouldBe LocalTime(9, 0)
        timeEvent.endTime shouldBe LocalTime(10, 30)
    }

    test("serializeEvents includes warning field only when present") {
        val withWarning = CriticJsonCodec.serializeEvents(
            listOf(
                DayEvent(
                    title = "A",
                    source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE,
                    date = LocalDate(2026, 6, 2),
                    warning = "Conflict"
                )
            )
        )
        val withoutWarning = CriticJsonCodec.serializeEvents(
            listOf(
                DayEvent(
                    title = "B",
                    source = EventSource.AI_GENERATED,
                    category = AcademicCategory.DEADLINE,
                    date = LocalDate(2026, 6, 2)
                )
            )
        )

        withWarning.contains("\"warning\":\"Conflict\"") shouldBe true
        withoutWarning.contains("warning") shouldBe false
    }

    test("parseEvents extracts an array nested under an 'events' key") {
        val json =
            """{"events": [{"title": "Quiz", "type": "DAY", "category": "DEADLINE", "date": "2026-06-05"}]}"""

        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Quiz"
    }

    test("parseEvents strips markdown code fences before parsing") {
        val json =
            "```json\n[{\"title\": \"Fenced\", \"type\": \"DAY\", \"category\": \"DEADLINE\", \"date\": \"2026-06-05\"}]\n```"

        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Fenced"
    }

    test("parseEvents throws when the JSON shape is neither an array nor an 'events' object") {
        val json = """{"unexpected": "shape"}"""

        try {
            CriticJsonCodec.parseEvents(json)
            error("Expected an exception to be thrown")
        } catch (e: Exception) {
            e.message?.contains("Unexpected JSON structure") shouldBe true
        }
    }

    test("parseEvents falls back to defaults for missing or invalid fields") {
        val json = """[{"title": "Sparse"}]"""

        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        val event = parsed[0] as DayEvent
        event.title shouldBe "Sparse"
        event.category shouldBe AcademicCategory.REGULAR
        event.date shouldBe LocalDate(2024, 1, 1)
    }

    test("parseEvents coerces explicit null fields to their DTO defaults") {
        val json = """[{"title": null, "category": null, "date": null, "warning": null}]"""

        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        val event = parsed[0] as DayEvent
        event.title shouldBe "Untitled Event"
        event.category shouldBe AcademicCategory.REGULAR
        event.date shouldBe LocalDate(2024, 1, 1)
        event.warning shouldBe null
    }

    test("parseEvents falls back to default times for an unparseable TIME event") {
        val json =
            """[{"title": "Bad Time", "type": "TIME", "category": "REGULAR", "date": "2026-06-02", "startTime": "not-a-time", "endTime": "also-bad"}]"""

        val parsed = CriticJsonCodec.parseEvents(json)

        val timeEvent = parsed[0] as TimeEvent
        timeEvent.startTime shouldBe LocalTime(9, 0)
        timeEvent.endTime shouldBe LocalTime(10, 0)
    }

    test("parseEvents skips malformed elements without failing the whole batch") {
        val json =
            """[{"title": "Good", "type": "DAY", "category": "DEADLINE", "date": "2026-06-02"}, "not-an-object"]"""

        val parsed = CriticJsonCodec.parseEvents(json)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Good"
    }

    test("serializeTasks round-trips through parseTasks") {
        val tasks = listOf(
            DecomposedTask(
                title = "Read Ch. 1",
                daysBeforeDue = 5,
                description = "Read the intro chapter"
            )
        )

        val json = CriticJsonCodec.serializeTasks(tasks)
        val parsed = CriticJsonCodec.parseTasks(json)

        parsed shouldBe tasks
    }

    test("parseTasks extracts an array nested under a 'tasks' key") {
        val json =
            """{"tasks": [{"title": "Outline", "daysBeforeDue": 3, "description": "Sketch the outline"}]}"""

        val parsed = CriticJsonCodec.parseTasks(json)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Outline"
    }

    test("parseTasks coerces a fractional daysBeforeDue value to an int") {
        val json = """[{"title": "Draft", "daysBeforeDue": 2.7, "description": "Write a draft"}]"""

        val parsed = CriticJsonCodec.parseTasks(json)

        parsed[0].daysBeforeDue shouldBe 2
    }

    test("parseTasks leniently coerces a numeric-string daysBeforeDue value to an int") {
        val json =
            """[{"title": "Lenient", "daysBeforeDue": "4", "description": "from a quoted number"}]"""

        val parsed = CriticJsonCodec.parseTasks(json)

        parsed[0].daysBeforeDue shouldBe 4
    }

    test("parseTasks falls back to defaults for missing fields") {
        val json = """[{}]"""

        val parsed = CriticJsonCodec.parseTasks(json)

        parsed.size shouldBe 1
        parsed[0].title shouldBe "Sub-task"
        parsed[0].daysBeforeDue shouldBe 1
        parsed[0].description shouldBe ""
    }

    test("parseTasks throws when the JSON shape is neither an array nor a 'tasks' object") {
        val json = """{"unexpected": "shape"}"""

        try {
            CriticJsonCodec.parseTasks(json)
            error("Expected an exception to be thrown")
        } catch (e: Exception) {
            e.message?.contains("Unexpected JSON structure") shouldBe true
        }
    }
})
