package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Lightweight facade coordinating calendar operations.
 * Delegates to specialized services for ID resolution, conflict detection, and filtering.
 */
class GoogleRemoteCalendarRepository(
    private val syncService: GoogleCalendarSyncService,
    private val preferencesRepository: PreferencesRepository,
    private val calendarIdResolver: CalendarIdResolver,
    private val conflictDetector: EventConflictDetector,
    private val eventFilter: EventRangeFilter
) : RemoteCalendarRepository {

    override fun getSettings(): com.russhwolf.settings.Settings? = null

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> =
        syncService.listCalendars()

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        return syncService.getEvents(targetId)
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        val existingEvents = syncService.getEvents(targetId)
        conflictDetector.validateNoConflict(event, existingEvents)
        syncService.syncEvent(event, targetId)
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        syncService.syncEvent(event, targetId)
    }

    override suspend fun deleteEvent(eventId: String, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            syncService.deleteEvent(targetId, eventId)
        } catch (e: GoogleApiException) {
            if (e.statusCode != 410) throw e
        }
    }

    override suspend fun hardDeleteEvent(eventId: String, calendarId: String) {
        deleteEvent(eventId, calendarId)
    }

    override suspend fun clearCalendar(calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        val events = syncService.getEvents(targetId)
        events.forEach { event ->
            event.id?.let { id ->
                try {
                    syncService.deleteEvent(targetId, id)
                } catch (e: GoogleApiException) {
                    if (e.statusCode != 410) throw e
                }
            }
        }
    }

    override suspend fun getEventsInRange(start: LocalDate, end: LocalDate, calendarId: String): List<Event> {
        val events = getAllEvents(calendarId)
        return eventFilter.filterByDateRange(events, start, end)
    }

    override suspend fun getEventsBySyncStatus(status: SyncStatus, calendarId: String): List<Event> {
        val events = getAllEvents(calendarId)
        return eventFilter.filterBySyncStatus(events, status)
    }

    override suspend fun getIncompleteEventsBefore(date: LocalDate, calendarId: String): List<Event> {
        val events = getAllEvents(calendarId)
        return eventFilter.filterIncompleteBeforeDate(events, date)
    }
}
