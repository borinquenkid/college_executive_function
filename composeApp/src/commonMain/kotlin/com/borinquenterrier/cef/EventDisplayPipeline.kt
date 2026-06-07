package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

object EventDisplayPipeline {
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
