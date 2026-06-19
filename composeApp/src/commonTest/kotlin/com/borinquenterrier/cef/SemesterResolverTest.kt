package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class SemesterResolverTest : FunSpec({

    test("getSemesterRange returns Fall semester (Aug 1 - Dec 31)") {
        val today1 = LocalDate(2026, 8, 15)
        val today2 = LocalDate(2026, 12, 1)

        val range1 = SemesterResolver.getSemesterRange(today1)
        val range2 = SemesterResolver.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31))
        range2 shouldBe (LocalDate(2026, 8, 1) to LocalDate(2026, 12, 31))
    }

    test("getSemesterRange returns Spring semester (Jan 1 - May 31)") {
        val today1 = LocalDate(2026, 1, 15)
        val today2 = LocalDate(2026, 5, 20)

        val range1 = SemesterResolver.getSemesterRange(today1)
        val range2 = SemesterResolver.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 1, 1) to LocalDate(2026, 5, 31))
        range2 shouldBe (LocalDate(2026, 1, 1) to LocalDate(2026, 5, 31))
    }

    test("getSemesterRange returns Summer semester (June 1 - Aug 31)") {
        val today1 = LocalDate(2026, 6, 15)
        val today2 = LocalDate(2026, 7, 30)

        val range1 = SemesterResolver.getSemesterRange(today1)
        val range2 = SemesterResolver.getSemesterRange(today2)

        range1 shouldBe (LocalDate(2026, 6, 1) to LocalDate(2026, 8, 31))
        range2 shouldBe (LocalDate(2026, 6, 1) to LocalDate(2026, 8, 31))
    }

    test("getExpandedRange returns base range when events are empty") {
        val today = LocalDate(2026, 6, 15)
        val range = SemesterResolver.getExpandedRange(today, emptyList())

        range shouldBe (LocalDate(2026, 6, 1) to LocalDate(2026, 8, 31))
    }

    test("getExpandedRange returns base range when all events lie inside base range") {
        val today = LocalDate(2026, 6, 15)
        val event = DayEvent(
            id = "e1",
            title = "Test",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 7, 20)
        )
        val range = SemesterResolver.getExpandedRange(today, listOf(event))

        range shouldBe (LocalDate(2026, 6, 1) to LocalDate(2026, 8, 31))
    }

    test("getExpandedRange expands range when events lie outside base range") {
        val today = LocalDate(2026, 6, 15)
        val event1 = DayEvent(
            id = "e1",
            title = "Past Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 5, 1) // before summer start (June 1)
        )
        val event2 = DayEvent(
            id = "e2",
            title = "Future Event",
            source = EventSource.MANUAL,
            date = LocalDate(2026, 9, 10) // after summer end (Aug 31)
        )
        val range = SemesterResolver.getExpandedRange(today, listOf(event1, event2))

        range shouldBe (LocalDate(2026, 5, 1) to LocalDate(2026, 9, 10))
    }

    // ── filterToActiveSemester ────────────────────────────────────────────────

    test("filterToActiveSemester keeps Summer and future events and drops Spring events") {
        val today = LocalDate(2026, 6, 19) // Summer
        val springDeadline = DayEvent(id = "s1", title = "Essay due",
            source = EventSource.AI_GENERATED, date = LocalDate(2026, 4, 15))
        val summerDeadline = DayEvent(id = "s2", title = "Issue Brief #1",
            source = EventSource.AI_GENERATED, date = LocalDate(2026, 7, 1),
            category = AcademicCategory.DEADLINE)
        val fallEvent = DayEvent(id = "s3", title = "Midterm",
            source = EventSource.AI_GENERATED, date = LocalDate(2026, 10, 5))

        val result = SemesterResolver.filterToActiveSemester(listOf(springDeadline, summerDeadline, fallEvent), today)

        result.map { it.id } shouldBe listOf("s2", "s3") // summer + fall (future semesters kept)
    }

    test("filterToActiveSemester keeps Fall events and drops earlier events") {
        val today = LocalDate(2026, 10, 1) // Fall
        val springEvent = DayEvent(id = "a", title = "Spring Final",
            source = EventSource.AI_GENERATED, date = LocalDate(2026, 5, 1))
        val summerEvent = DayEvent(id = "b", title = "Summer class",
            source = EventSource.AI_GENERATED, date = LocalDate(2026, 7, 1))
        val fallEvent   = DayEvent(id = "c", title = "Midterm",
            source = EventSource.AI_GENERATED, date = LocalDate(2026, 10, 15))

        val result = SemesterResolver.filterToActiveSemester(listOf(springEvent, summerEvent, fallEvent), today)

        result.map { it.id } shouldBe listOf("c") // only fall
    }

    test("filterToActiveSemester on empty list returns empty") {
        SemesterResolver.filterToActiveSemester(emptyList(), LocalDate(2026, 9, 1)) shouldBe emptyList<Event>()
    }

    // ── isDecomposable ────────────────────────────────────────────────────────

    test("isDecomposable returns true for DEADLINE and FINALS, false for everything else") {
        fun dayEvent(cat: AcademicCategory) =
            DayEvent(title = "Test", source = EventSource.AI_GENERATED,
                date = LocalDate(2026, 7, 1), category = cat)

        CalendarEventGrouper.isDecomposable(dayEvent(AcademicCategory.DEADLINE))    shouldBe true
        CalendarEventGrouper.isDecomposable(dayEvent(AcademicCategory.FINALS))      shouldBe true
        CalendarEventGrouper.isDecomposable(dayEvent(AcademicCategory.CLASS))       shouldBe false
        CalendarEventGrouper.isDecomposable(dayEvent(AcademicCategory.REGULAR))     shouldBe false
        CalendarEventGrouper.isDecomposable(dayEvent(AcademicCategory.STUDY_BLOCK)) shouldBe false
    }

    // ── dedup: category-agnostic title+date match ─────────────────────────────

    test("push dedup treats same title+date as duplicate even when category changed REGULAR to DEADLINE") {
        val existing = listOf(
            DayEvent(id = "old-id", title = "Issue Brief #1",
                source = EventSource.AI_GENERATED, date = LocalDate(2026, 7, 1),
                category = AcademicCategory.REGULAR)
        )
        val proposed = listOf(
            DayEvent(id = "new-id", title = "Issue Brief #1",
                source = EventSource.AI_GENERATED, date = LocalDate(2026, 7, 1),
                category = AcademicCategory.DEADLINE)
        )

        // mirrors CalendarPushResolver Phase 2 filter
        val isNew = proposed.filter { merged ->
            existing.none { it.id == merged.id } &&
            existing.none { it.title == merged.title && it.date == merged.date }
        }
        isNew shouldBe emptyList<Event>()
    }
})
