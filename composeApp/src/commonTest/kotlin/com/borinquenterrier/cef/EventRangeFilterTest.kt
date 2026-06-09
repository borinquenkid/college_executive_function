package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class EventRangeFilterTest : FunSpec({
    val filter = EventRangeFilter()

    test("filterByDateRange includes events within range") {
        val events = listOf(
            TimeEvent(
                id = "1",
                title = "Event 1",
                date = LocalDate(2026, 6, 10),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            ),
            TimeEvent(
                id = "2",
                title = "Event 2",
                date = LocalDate(2026, 6, 15),
                startTime = LocalTime(14, 0),
                endTime = LocalTime(15, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val result = filter.filterByDateRange(
            events,
            LocalDate(2026, 6, 1),
            LocalDate(2026, 6, 30)
        )

        result.shouldHaveSize(2)
    }

    test("filterByDateRange excludes events outside range") {
        val events = listOf(
            TimeEvent(
                id = "1",
                title = "Before",
                date = LocalDate(2026, 5, 31),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            ),
            TimeEvent(
                id = "2",
                title = "After",
                date = LocalDate(2026, 7, 1),
                startTime = LocalTime(14, 0),
                endTime = LocalTime(15, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val result = filter.filterByDateRange(
            events,
            LocalDate(2026, 6, 1),
            LocalDate(2026, 6, 30)
        )

        result.shouldBeEmpty()
    }

    test("filterBySyncStatus returns all for SYNCED status") {
        val events = listOf(
            TimeEvent(
                id = "1",
                title = "Event",
                date = LocalDate(2026, 6, 10),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val result = filter.filterBySyncStatus(events, SyncStatus.SYNCED)

        result.shouldHaveSize(1)
    }

    test("filterBySyncStatus returns empty for non-SYNCED status") {
        val events = listOf(
            TimeEvent(
                id = "1",
                title = "Event",
                date = LocalDate(2026, 6, 10),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val result = filter.filterBySyncStatus(events, SyncStatus.LOCAL_ONLY)

        result.shouldBeEmpty()
    }

    test("filterIncompleteBeforeDate filters by completion and date") {
        val events = listOf(
            TimeEvent(
                id = "1",
                title = "Incomplete Past",
                date = LocalDate(2026, 6, 5),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                completionStatus = CompletionStatus.INCOMPLETE
            ),
            TimeEvent(
                id = "2",
                title = "Complete Past",
                date = LocalDate(2026, 6, 5),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                completionStatus = CompletionStatus.COMPLETED
            ),
            TimeEvent(
                id = "3",
                title = "Incomplete Future",
                date = LocalDate(2026, 6, 20),
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.AI_GENERATED,
                category = AcademicCategory.STUDY_BLOCK,
                completionStatus = CompletionStatus.INCOMPLETE
            )
        )

        val result = filter.filterIncompleteBeforeDate(
            events,
            LocalDate(2026, 6, 15)
        )

        result.shouldHaveSize(1)
    }
})
