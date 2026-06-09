package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Groups events by date and prepares them for display.
 * Handles filtering, expansion, and date-based organization.
 */
object CalendarEventGrouper {

    /**
     * Groups events by date for display in calendar view.
     * Handles both TimeEvent and DayEvent types.
     */
    fun groupEventsByDate(events: List<Event>): Map<LocalDate, List<Event>> {
        return events.groupBy { event ->
            when (event) {
                is TimeEvent -> event.date
                is DayEvent -> event.date
            }
        }
    }

    /**
     * Determines if an event can be decomposed (broken down into subtasks).
     * Only DEADLINE and FINALS events are decomposable.
     */
    fun isDecomposable(event: Event): Boolean {
        return event.category == AcademicCategory.DEADLINE || event.category == AcademicCategory.FINALS
    }

    /**
     * Filters events to only include those within a given date range.
     */
    fun filterEventsByDateRange(
        events: List<Event>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Event> {
        return events.filter { event ->
            val eventDate = when (event) {
                is TimeEvent -> event.date
                is DayEvent -> event.date
            }
            eventDate in startDate..endDate
        }
    }
}
