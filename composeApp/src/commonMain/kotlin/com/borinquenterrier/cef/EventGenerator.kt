package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object EventGenerator {

    /**
     * Expands a list of potentially recurring events into a list of concrete, single-day events
     * for a given date range.
     */
    fun expandEvents(
        events: List<Event>,
        viewStartDate: LocalDate,
        viewEndDate: LocalDate
    ): List<Event> {
        return events.flatMap { event ->
            when (event) {
                is TimeEvent -> expandTimeEvent(event, viewStartDate, viewEndDate)
                is DayEvent -> expandDayEvent(event, viewStartDate, viewEndDate)
            }
        }
    }

    private fun expandTimeEvent(
        event: TimeEvent,
        viewStartDate: LocalDate,
        viewEndDate: LocalDate
    ): List<TimeEvent> {
        val recurrence = event.recurrence
            ?: return if (event.date in viewStartDate..viewEndDate) listOf(event) else emptyList()

        return generateSequence(viewStartDate) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= viewEndDate }
            .filter { date ->
                date.dayOfWeek in recurrence.daysOfWeek &&
                        date >= recurrence.startDate &&
                        date <= recurrence.endDate
            }
            .map { date ->
                // Create a concrete instance for this specific date
                event.copy(date = date, recurrence = null)
            }
            .toList()
    }

    private fun expandDayEvent(
        event: DayEvent,
        viewStartDate: LocalDate,
        viewEndDate: LocalDate
    ): List<DayEvent> {
        val recurrence = event.recurrence
            ?: return if (event.date in viewStartDate..viewEndDate) listOf(event) else emptyList()

        return generateSequence(viewStartDate) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= viewEndDate }
            .filter { date ->
                date.dayOfWeek in recurrence.daysOfWeek &&
                        date >= recurrence.startDate &&
                        date <= recurrence.endDate
            }
            .map { date ->
                // Create a concrete instance for this specific date
                event.copy(date = date, recurrence = null)
            }
            .toList()
    }
}
