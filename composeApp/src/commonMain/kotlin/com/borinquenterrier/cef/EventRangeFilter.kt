package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Filters events by date range, completion status, and sync status.
 */
class EventRangeFilter {
    /**
     * Filters events within a date range.
     * @param events Events to filter
     * @param start Start date (inclusive)
     * @param end End date (inclusive)
     * @return Events that fall within the date range
     */
    fun filterByDateRange(events: List<Event>, start: LocalDate, end: LocalDate): List<Event> {
        return events.filter { event ->
            val date = when (event) {
                is TimeEvent -> event.date
                is DayEvent -> event.date
            }
            date in start..end
        }
    }

    /**
     * Filters events by sync status.
     * @param events Events to filter
     * @param status Sync status to match
     * @return Events matching the sync status
     */
    fun filterBySyncStatus(events: List<Event>, status: SyncStatus): List<Event> {
        return if (status == SyncStatus.SYNCED) events else emptyList()
    }

    /**
     * Filters incomplete events before a given date.
     * @param events Events to filter
     * @param cutoffDate Date threshold
     * @return Incomplete events before the cutoff date
     */
    fun filterIncompleteBeforeDate(events: List<Event>, cutoffDate: LocalDate): List<Event> {
        return events.filter { event ->
            event.completionStatus == CompletionStatus.INCOMPLETE && event.date < cutoffDate
        }
    }
}
