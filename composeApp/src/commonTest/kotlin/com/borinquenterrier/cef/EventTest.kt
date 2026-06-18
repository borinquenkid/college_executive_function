package com.borinquenterrier.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json

class EventTest : FunSpec({

    fun day(
        title: String = "HW1",
        date: LocalDate = LocalDate(2025, 10, 15),
        id: String? = null,
        warning: String? = null,
        studyPlanStart: String? = null,
        category: AcademicCategory = AcademicCategory.DEADLINE
    ) = DayEvent(
        id = id, title = title, source = EventSource.AI_GENERATED,
        category = category, date = date, warning = warning, studyPlanStart = studyPlanStart
    )

    fun time(
        title: String = "Class",
        date: LocalDate = LocalDate(2025, 10, 15),
        start: LocalTime = LocalTime(9, 0),
        end: LocalTime = LocalTime(10, 0)
    ) = TimeEvent(
        title = title, source = EventSource.AI_GENERATED, date = date,
        startTime = start, endTime = end, category = AcademicCategory.CLASS
    )

    // ── AcademicCategory.priority ──────────────────────────────────────────────

    test("priority: FINALS > DEADLINE > CLASS > HOLIDAY > SEMESTER_BOUND > REGULAR > STUDY_BLOCK") {
        AcademicCategory.FINALS.priority shouldBe 100
        AcademicCategory.DEADLINE.priority shouldBe 90
        AcademicCategory.CLASS.priority shouldBe 80
        AcademicCategory.HOLIDAY.priority shouldBe 70
        AcademicCategory.SEMESTER_BOUND.priority shouldBe 60
        AcademicCategory.REGULAR.priority shouldBe 30
        AcademicCategory.STUDY_BLOCK.priority shouldBe 10
    }

    // ── TimeEvent.overlaps ─────────────────────────────────────────────────────

    test("TimeEvent overlaps when intervals share a range on the same date") {
        val a = time(start = LocalTime(9, 0), end = LocalTime(11, 0))
        val b = time(start = LocalTime(10, 0), end = LocalTime(12, 0))
        a.overlaps(b) shouldBe true
        b.overlaps(a) shouldBe true
    }

    test("TimeEvent does not overlap when intervals are adjacent (end == start)") {
        val a = time(start = LocalTime(9, 0), end = LocalTime(10, 0))
        val b = time(start = LocalTime(10, 0), end = LocalTime(11, 0))
        a.overlaps(b) shouldBe false
    }

    test("TimeEvent does not overlap when on different dates") {
        val a = time(date = LocalDate(2025, 10, 15), start = LocalTime(9, 0), end = LocalTime(11, 0))
        val b = time(date = LocalDate(2025, 10, 16), start = LocalTime(9, 0), end = LocalTime(11, 0))
        a.overlaps(b) shouldBe false
    }

    test("TimeEvent does not overlap with DayEvent") {
        val a = time()
        val b = day()
        a.overlaps(b) shouldBe false
    }

    test("DayEvent never overlaps anything") {
        val a = day()
        val b = day()
        a.overlaps(b) shouldBe false
        a.overlaps(time()) shouldBe false
    }

    // ── Event.validate ─────────────────────────────────────────────────────────

    test("validate passes for a valid DayEvent") {
        day().validate()
    }

    test("validate throws when title is blank") {
        shouldThrow<IllegalArgumentException> { day(title = "  ").validate() }
    }

    test("validate passes for valid TimeEvent") {
        time().validate()
    }

    test("validate throws when TimeEvent start >= end") {
        shouldThrow<IllegalArgumentException> {
            time(start = LocalTime(10, 0), end = LocalTime(9, 0)).validate()
        }
    }

    // ── withSyncStatus / withCompletionStatus ─────────────────────────────────

    test("DayEvent.withSyncStatus copies with new status") {
        val event = day().withSyncStatus(SyncStatus.DELETED_LOCALLY)
        event.syncStatus shouldBe SyncStatus.DELETED_LOCALLY
    }

    test("TimeEvent.withSyncStatus copies with new status") {
        val event = time().withSyncStatus(SyncStatus.LOCAL_ONLY)
        event.syncStatus shouldBe SyncStatus.LOCAL_ONLY
    }

    test("DayEvent.withCompletionStatus copies with new status") {
        val event = day().withCompletionStatus(CompletionStatus.COMPLETED)
        event.completionStatus shouldBe CompletionStatus.COMPLETED
    }

    test("TimeEvent.withCompletionStatus copies with new status") {
        val event = time().withCompletionStatus(CompletionStatus.SKIPPED)
        event.completionStatus shouldBe CompletionStatus.SKIPPED
    }

    // ── Event.studyProgress ────────────────────────────────────────────────────

    test("studyProgress returns 0 for non-DEADLINE categories") {
        val event = day(category = AcademicCategory.REGULAR, date = LocalDate(2025, 10, 15))
        event.studyProgress(LocalDate(2025, 10, 10)) shouldBeExactly 0f
    }

    test("studyProgress returns 0 when study has not started yet") {
        // due 2025-10-15, start defaults to 7 days before = 2025-10-08, currentDate before start
        val event = day(category = AcademicCategory.DEADLINE, date = LocalDate(2025, 10, 15))
        event.studyProgress(LocalDate(2025, 10, 7)) shouldBeExactly 0f
    }

    test("studyProgress returns 1 when past due date") {
        val event = day(category = AcademicCategory.DEADLINE, date = LocalDate(2025, 10, 15))
        event.studyProgress(LocalDate(2025, 10, 16)) shouldBeExactly 1f
    }

    test("studyProgress returns midpoint at halfway through study window") {
        // start = 2025-10-01, due = 2025-10-15 → 14 days window, halfway = 2025-10-08
        val event = day(
            category = AcademicCategory.DEADLINE, date = LocalDate(2025, 10, 15),
            studyPlanStart = "2025-10-01"
        )
        val progress = event.studyProgress(LocalDate(2025, 10, 8))
        // elapsed = 7, total = 14 → 0.5
        (progress > 0.49f && progress < 0.51f) shouldBe true
    }

    test("studyProgress uses studyPlanStart when provided") {
        val event = day(
            category = AcademicCategory.DEADLINE, date = LocalDate(2025, 10, 15),
            studyPlanStart = "2025-10-12"
        )
        // start = Oct 12, due = Oct 15 (3 days), current = Oct 13 → 1/3
        val progress = event.studyProgress(LocalDate(2025, 10, 13))
        (progress > 0.3f && progress < 0.34f) shouldBe true
    }

    test("studyProgress handles invalid studyPlanStart by falling back to 7 days before due") {
        val event = day(
            category = AcademicCategory.DEADLINE, date = LocalDate(2025, 10, 15),
            studyPlanStart = "not-a-date"
        )
        // Fallback: start = 2025-10-08, due = 2025-10-15
        // At Oct 14 (6 days elapsed out of 7) → 6/7
        val progress = event.studyProgress(LocalDate(2025, 10, 14))
        (progress > 0.85f && progress < 0.86f) shouldBe true
    }

    // ── Serialization round-trip ───────────────────────────────────────────────

    test("DayEvent serializes and deserializes correctly") {
        val event = day(id = "abc", title = "Test HW", warning = "Due tonight")
        val json = Json.encodeToString(Event.serializer(), event)
        val decoded = Json.decodeFromString(Event.serializer(), json)
        decoded shouldBe event
    }

    test("TimeEvent serializes and deserializes correctly") {
        val event = time(title = "Math Class", start = LocalTime(14, 30), end = LocalTime(15, 45))
        val json = Json.encodeToString(Event.serializer(), event)
        val decoded = Json.decodeFromString(Event.serializer(), json)
        decoded shouldBe event
    }
})
