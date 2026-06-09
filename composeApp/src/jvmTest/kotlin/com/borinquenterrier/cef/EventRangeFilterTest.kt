package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class EventRangeFilterTest : FunSpec({
    val filter = EventRangeFilter()

    test("filterByDateRange includes events within range") {
        val event1 = mockk<TimeEvent> {
            every { date } returns LocalDate(2024, 1, 5)
        }
        val event2 = mockk<TimeEvent> {
            every { date } returns LocalDate(2024, 1, 15)
        }
        val event3 = mockk<TimeEvent> {
            every { date } returns LocalDate(2024, 2, 1)
        }

        val events = listOf(event1, event2, event3)
        val result = filter.filterByDateRange(events, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

        result shouldContainExactly listOf(event1, event2)
    }

    test("filterByDateRange excludes events outside range") {
        val event1 = mockk<TimeEvent> {
            every { date } returns LocalDate(2023, 12, 31)
        }
        val event2 = mockk<TimeEvent> {
            every { date } returns LocalDate(2024, 2, 1)
        }

        val events = listOf(event1, event2)
        val result = filter.filterByDateRange(events, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

        result.shouldBeEmpty()
    }

    test("filterByDateRange includes events on boundaries") {
        val startEvent = mockk<TimeEvent> {
            every { date } returns LocalDate(2024, 1, 1)
        }
        val endEvent = mockk<TimeEvent> {
            every { date } returns LocalDate(2024, 1, 31)
        }

        val events = listOf(startEvent, endEvent)
        val result = filter.filterByDateRange(events, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

        result shouldHaveSize 2
    }

    test("filterByDateRange handles DayEvent dates") {
        val dayEvent = mockk<DayEvent> {
            every { date } returns LocalDate(2024, 1, 15)
        }

        val events = listOf(dayEvent)
        val result = filter.filterByDateRange(events, LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

        result shouldContainExactly listOf(dayEvent)
    }

    test("filterByDateRange handles empty event list") {
        val result = filter.filterByDateRange(emptyList(), LocalDate(2024, 1, 1), LocalDate(2024, 1, 31))

        result.shouldBeEmpty()
    }

    test("filterBySyncStatus returns events for SYNCED status") {
        val event1 = mockk<Event>()
        val event2 = mockk<Event>()

        val events = listOf(event1, event2)
        val result = filter.filterBySyncStatus(events, SyncStatus.SYNCED)

        result shouldContainExactly listOf(event1, event2)
    }

    test("filterBySyncStatus returns empty for non-SYNCED status") {
        val event1 = mockk<Event>()
        val event2 = mockk<Event>()

        val events = listOf(event1, event2)
        val result = filter.filterBySyncStatus(events, SyncStatus.LOCAL_ONLY)

        result.shouldBeEmpty()
    }

    test("filterBySyncStatus handles empty list") {
        val result = filter.filterBySyncStatus(emptyList(), SyncStatus.SYNCED)

        result.shouldBeEmpty()
    }

    test("filterIncompleteBeforeDate filters incomplete events before cutoff") {
        val incompleteOld = mockk<Event> {
            every { completionStatus } returns CompletionStatus.INCOMPLETE
            every { date } returns LocalDate(2024, 1, 10)
        }
        val completeOld = mockk<Event> {
            every { completionStatus } returns CompletionStatus.COMPLETED
            every { date } returns LocalDate(2024, 1, 10)
        }
        val incompleteNew = mockk<Event> {
            every { completionStatus } returns CompletionStatus.INCOMPLETE
            every { date } returns LocalDate(2024, 2, 1)
        }

        val events = listOf(incompleteOld, completeOld, incompleteNew)
        val result = filter.filterIncompleteBeforeDate(events, LocalDate(2024, 1, 31))

        result shouldContainExactly listOf(incompleteOld)
    }

    test("filterIncompleteBeforeDate excludes events on cutoff date") {
        val event = mockk<Event> {
            every { completionStatus } returns CompletionStatus.INCOMPLETE
            every { date } returns LocalDate(2024, 1, 31)
        }

        val result = filter.filterIncompleteBeforeDate(listOf(event), LocalDate(2024, 1, 31))

        result.shouldBeEmpty()
    }

    test("filterIncompleteBeforeDate returns empty when no incomplete events") {
        val event = mockk<Event> {
            every { completionStatus } returns CompletionStatus.COMPLETED
            every { date } returns LocalDate(2024, 1, 10)
        }

        val result = filter.filterIncompleteBeforeDate(listOf(event), LocalDate(2024, 1, 31))

        result.shouldBeEmpty()
    }
})
