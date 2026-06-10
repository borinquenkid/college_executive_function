package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarEventGrouperTest {

    @Test
    fun `groupEventsByDate groups TimeEvents correctly`() {
        val date1 = LocalDate(2024, 1, 1)
        val date2 = LocalDate(2024, 1, 2)
        val events = listOf(
            TimeEvent(
                title = "Event 1",
                date = date1,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.ROUTINE,
                category = AcademicCategory.CLASS
            ),
            TimeEvent(
                title = "Event 2",
                date = date1,
                startTime = LocalTime(14, 0),
                endTime = LocalTime(15, 0),
                source = EventSource.ROUTINE,
                category = AcademicCategory.CLASS
            ),
            TimeEvent(
                title = "Event 3",
                date = date2,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.ROUTINE,
                category = AcademicCategory.CLASS
            )
        )

        val grouped = CalendarEventGrouper.groupEventsByDate(events)

        assertEquals(2, grouped.size)
        assertEquals(2, grouped[date1]?.size)
        assertEquals(1, grouped[date2]?.size)
    }

    @Test
    fun `groupEventsByDate groups DayEvents correctly`() {
        val date1 = LocalDate(2024, 1, 1)
        val date2 = LocalDate(2024, 1, 2)
        val events = listOf(
            DayEvent(
                title = "All Day 1",
                date = date1,
                source = EventSource.ROUTINE,
                category = AcademicCategory.HOLIDAY
            ),
            DayEvent(
                title = "All Day 2",
                date = date2,
                source = EventSource.ROUTINE,
                category = AcademicCategory.HOLIDAY
            )
        )

        val grouped = CalendarEventGrouper.groupEventsByDate(events)

        assertEquals(2, grouped.size)
        assertEquals(1, grouped[date1]?.size)
        assertEquals(1, grouped[date2]?.size)
    }

    @Test
    fun `groupEventsByDate handles mixed event types`() {
        val date = LocalDate(2024, 1, 1)
        val events = listOf(
            TimeEvent(
                title = "Timed",
                date = date,
                startTime = LocalTime(10, 0),
                endTime = LocalTime(11, 0),
                source = EventSource.ROUTINE,
                category = AcademicCategory.CLASS
            ),
            DayEvent(
                title = "All Day",
                date = date,
                source = EventSource.ROUTINE,
                category = AcademicCategory.HOLIDAY
            )
        )

        val grouped = CalendarEventGrouper.groupEventsByDate(events)

        assertEquals(1, grouped.size)
        assertEquals(2, grouped[date]?.size)
    }

    @Test
    fun `isDecomposable returns true for DEADLINE events`() {
        val event = DayEvent(
            title = "Assignment",
            date = LocalDate(2024, 1, 1),
            source = EventSource.ROUTINE,
            category = AcademicCategory.DEADLINE
        )

        assertTrue(CalendarEventGrouper.isDecomposable(event))
    }

    @Test
    fun `isDecomposable returns true for FINALS events`() {
        val event = DayEvent(
            title = "Final Exam",
            date = LocalDate(2024, 1, 1),
            source = EventSource.ROUTINE,
            category = AcademicCategory.FINALS
        )

        assertTrue(CalendarEventGrouper.isDecomposable(event))
    }

    @Test
    fun `isDecomposable returns false for CLASS events`() {
        val event = TimeEvent(
            title = "Class",
            date = LocalDate(2024, 1, 1),
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            source = EventSource.ROUTINE,
            category = AcademicCategory.CLASS
        )

        assertFalse(CalendarEventGrouper.isDecomposable(event))
    }

    @Test
    fun `isDecomposable returns false for STUDY_BLOCK events`() {
        val event = DayEvent(
            title = "Study Block",
            date = LocalDate(2024, 1, 1),
            source = EventSource.ROUTINE,
            category = AcademicCategory.STUDY_BLOCK
        )

        assertFalse(CalendarEventGrouper.isDecomposable(event))
    }

    @Test
    fun `filterEventsByDateRange includes events within range`() {
        val startDate = LocalDate(2024, 1, 1)
        val endDate = LocalDate(2024, 1, 31)
        val events = listOf(
            DayEvent(
                title = "Event 1",
                date = LocalDate(2024, 1, 5),
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            ),
            DayEvent(
                title = "Event 2",
                date = LocalDate(2024, 1, 15),
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            ),
            DayEvent(
                title = "Event 3",
                date = LocalDate(2024, 2, 5),
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val filtered = CalendarEventGrouper.filterEventsByDateRange(events, startDate, endDate)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterEventsByDateRange includes boundary dates`() {
        val startDate = LocalDate(2024, 1, 1)
        val endDate = LocalDate(2024, 1, 31)
        val events = listOf(
            DayEvent(
                title = "Start",
                date = startDate,
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            ),
            DayEvent(
                title = "End",
                date = endDate,
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val filtered = CalendarEventGrouper.filterEventsByDateRange(events, startDate, endDate)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterEventsByDateRange excludes events outside range`() {
        val startDate = LocalDate(2024, 1, 1)
        val endDate = LocalDate(2024, 1, 31)
        val events = listOf(
            DayEvent(
                title = "Before",
                date = LocalDate(2023, 12, 31),
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            ),
            DayEvent(
                title = "After",
                date = LocalDate(2024, 2, 1),
                source = EventSource.ROUTINE,
                category = AcademicCategory.STUDY_BLOCK
            )
        )

        val filtered = CalendarEventGrouper.filterEventsByDateRange(events, startDate, endDate)

        assertEquals(0, filtered.size)
    }
}
