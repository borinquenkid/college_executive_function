package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventConflictDetectorTest : FunSpec({
    val detector = EventConflictDetector()

    test("findConflict returns null when no conflicts") {
        val newEvent = TimeEvent(
            id = "new",
            title = "New Event",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )
        val existing = listOf(
            TimeEvent(
                id = "existing",
                title = "Existing",
                date = LocalDate(2026, 6, 10),
                startTime = LocalTime(16, 0),
                endTime = LocalTime(17, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val result = detector.findConflict(newEvent, existing)

        result shouldBe null
    }

    test("findConflict detects overlapping event") {
        val newEvent = TimeEvent(
            id = "new",
            title = "New Event",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )
        val conflicting = TimeEvent(
            id = "conflict",
            title = "Conflicting",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 30),
            endTime = LocalTime(15, 30),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )

        val result = detector.findConflict(newEvent, listOf(conflicting))

        result shouldBe conflicting
    }

    test("findConflict ignores same ID") {
        val eventId = "same-event"
        val newEvent = TimeEvent(
            id = eventId,
            title = "Event",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )
        val existing = listOf(
            TimeEvent(
                id = eventId,
                title = "Event",
                date = LocalDate(2026, 6, 10),
                startTime = LocalTime(14, 0),
                endTime = LocalTime(15, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val result = detector.findConflict(newEvent, existing)

        result shouldBe null
    }

    test("validateNoConflict throws on conflict") {
        val newEvent = TimeEvent(
            id = "new",
            title = "New",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )
        val conflicting = TimeEvent(
            id = "conflict",
            title = "Conflicting",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 30),
            endTime = LocalTime(15, 30),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )

        shouldThrow<OverlapException> {
            detector.validateNoConflict(newEvent, listOf(conflicting))
        }
    }

    test("validateNoConflict succeeds with no conflicts") {
        val newEvent = TimeEvent(
            id = "new",
            title = "New Event",
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 0),
            source = EventSource.AI_GENERATED,
            category = AcademicCategory.STUDY_BLOCK
        )
        val existing = listOf(
            TimeEvent(
                id = "existing",
                title = "Existing",
                date = LocalDate(2026, 6, 10),
                startTime = LocalTime(16, 0),
                endTime = LocalTime(17, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        detector.validateNoConflict(newEvent, existing)
    }
})
