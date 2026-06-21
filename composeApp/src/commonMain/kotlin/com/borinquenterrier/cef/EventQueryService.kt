package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

class EventQueryService(
    private val getAllEvents: suspend (String) -> List<Event>,
    private val filter: EventRangeFilter = EventRangeFilter()
) {
    suspend fun getEventsInRange(calendarId: String, start: LocalDate, end: LocalDate): List<Event> =
        filter.filterByDateRange(getAllEvents(calendarId), start, end)

    suspend fun getEventsBySyncStatus(calendarId: String, status: SyncStatus): List<Event> =
        filter.filterBySyncStatus(getAllEvents(calendarId), status)

    suspend fun getIncompleteEventsBefore(calendarId: String, cutoffDate: LocalDate): List<Event> =
        filter.filterIncompleteBeforeDate(getAllEvents(calendarId), cutoffDate)
}
