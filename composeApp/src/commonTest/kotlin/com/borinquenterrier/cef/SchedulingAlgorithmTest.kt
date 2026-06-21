package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// Default working hours: 9:00–21:00, lunch 12:00–13:00, dinner 17:00–19:00.
class SchedulingAlgorithmTest : FunSpec({

    fun algo(maxDepth: Int = 3) =
        SchedulingAlgorithm(maxDepth, ConstraintValidator(), CollisionDetector())

    fun studyBlock(
        date: LocalDate = LocalDate(2026, 6, 10),
        start: LocalTime = LocalTime(10, 0),
        end: LocalTime = LocalTime(11, 0)
    ) = TimeEvent(
        title = "Study",
        source = EventSource.AI_GENERATED,
        date = date,
        startTime = start,
        endTime = end,
        category = AcademicCategory.STUDY_BLOCK
    )

    // ── Non-schedulable events ────────────────────────────────────────────────

    test("DayEvent resolves to Success immediately without scheduling logic") {
        val dayEvent = DayEvent(
            title = "Holiday",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 6, 10),
            category = AcademicCategory.HOLIDAY
        )
        val result = algo().resolve(dayEvent, emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        (result as ResolutionResult.Success).resolvedEvents[0].title shouldBe "Holiday"
    }

    test("non-STUDY_BLOCK TimeEvent resolves to Success immediately") {
        val lecture = TimeEvent(
            title = "Lecture",
            source = EventSource.AI_GENERATED,
            date = LocalDate(2026, 6, 10),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            category = AcademicCategory.CLASS
        )
        val result = algo().resolve(lecture, emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        (result as ResolutionResult.Success).resolvedEvents[0].title shouldBe "Lecture"
    }

    test("multiple DayEvents on different dates both resolve to Success") {
        val e1 = DayEvent(title = "E1", source = EventSource.MANUAL, date = LocalDate(2026, 6, 10), category = AcademicCategory.REGULAR)
        val e2 = DayEvent(title = "E2", source = EventSource.MANUAL, date = LocalDate(2026, 6, 11), category = AcademicCategory.REGULAR)
        val r1 = algo().resolve(e1, emptyList()) as ResolutionResult.Success
        val r2 = algo().resolve(e2, r1.resolvedEvents)
        r2.shouldBeInstanceOf<ResolutionResult.Success>()
    }

    // ── Depth limit ───────────────────────────────────────────────────────────

    test("returns Conflict when depth exceeds maxDepth") {
        val result = algo(maxDepth = 0).resolve(studyBlock(), emptyList(), depth = 1)
        result.shouldBeInstanceOf<ResolutionResult.Conflict>()
    }

    // ── STUDY_BLOCK — valid slot ──────────────────────────────────────────────

    test("STUDY_BLOCK in valid slot with no existing events resolves to Success") {
        val result = algo().resolve(studyBlock(start = LocalTime(10, 0), end = LocalTime(11, 0)), emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        (result as ResolutionResult.Success).resolvedEvents.size shouldBe 1
    }

    // ── STUDY_BLOCK — invalid slot, shift succeeds ────────────────────────────

    test("STUDY_BLOCK before working hours is shifted to a valid slot") {
        // 8:00–9:00 is before the 9:00 working-hour start → must shift to 9:00+
        val event = studyBlock(start = LocalTime(8, 0), end = LocalTime(9, 0))
        val result = algo().resolve(event, emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val resolved = (result as ResolutionResult.Success).resolvedEvents.single() as TimeEvent
        resolved.startTime shouldBe LocalTime(9, 0)
    }

    test("STUDY_BLOCK overlapping lunch break is shifted to a valid slot") {
        // 11:30–12:30 overlaps lunch (12:00–13:00)
        val event = studyBlock(start = LocalTime(11, 30), end = LocalTime(12, 30))
        val result = algo().resolve(event, emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Success>()
        val resolved = (result as ResolutionResult.Success).resolvedEvents.single() as TimeEvent
        // Shifted slot must not overlap lunch
        (resolved.startTime >= LocalTime(13, 0) || resolved.endTime <= LocalTime(12, 0)) shouldBe true
    }

    // ── STUDY_BLOCK — shift returns null (duration exceeds working window) ─────

    test("STUDY_BLOCK longer than working window returns Conflict when no slot fits") {
        // 13-hour duration > 12-hour working window (9:00–21:00), so findNextTimeSlot always returns null
        val event = studyBlock(start = LocalTime(7, 0), end = LocalTime(20, 0))
        val result = algo().resolve(event, emptyList())
        result.shouldBeInstanceOf<ResolutionResult.Conflict>()
    }
})
