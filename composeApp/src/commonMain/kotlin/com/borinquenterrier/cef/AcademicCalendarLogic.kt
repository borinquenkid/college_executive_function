package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.atStartOfDayIn

object AcademicCalendarLogic {

    /**
     * Determines the active semester date range based on the current date.
     * Fall semester is defined as Aug 1 - Dec 31.
     * Spring semester is defined as Jan 1 - May 31.
     * Summer/interim defaults to today to today + 30 days.
     */
    fun getSemesterRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val currentYear = today.year
        val isFirstSemester = today.monthNumber in 8..12
        val isSecondSemester = today.monthNumber in 1..5

        return when {
            isFirstSemester -> {
                LocalDate(currentYear, 8, 1) to LocalDate(currentYear, 12, 31)
            }
            isSecondSemester -> {
                LocalDate(currentYear, 1, 1) to LocalDate(currentYear, 5, 31)
            }
            else -> {
                today to today.plus(30, DateTimeUnit.DAY)
            }
        }
    }

    /**
     * Expands routine and AI events, merges them with remote/displayed events,
     * filters them to be within the specified start/end date range, and sorts them by date.
     */
    fun getExpandedAndFilteredEvents(
        routineEvents: List<Event>,
        aiGeneratedEvents: List<Event>,
        displayedEvents: List<Event>,
        startDate: LocalDate,
        endDate: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<Event> {
        val expandedRoutineEvents = EventGenerator.expandEvents(routineEvents, startDate, endDate)
        val expandedAiEvents = EventGenerator.expandEvents(aiGeneratedEvents, startDate, endDate)

        return (expandedRoutineEvents + expandedAiEvents + displayedEvents)
            .filter { event ->
                val date = when (event) {
                    is TimeEvent -> event.date
                    is DayEvent -> event.date
                }
                date in startDate..endDate
            }
            .sortedBy { event ->
                when (event) {
                    is TimeEvent -> event.date.atStartOfDayIn(timeZone)
                    is DayEvent -> event.date.atStartOfDayIn(timeZone)
                }
            }
    }
}
