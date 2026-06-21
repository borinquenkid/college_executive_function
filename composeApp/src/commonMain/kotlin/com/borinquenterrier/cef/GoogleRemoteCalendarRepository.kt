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
    private val tag = "GoogleRemoteCalendarRepository"

    override fun getSettings(): com.russhwolf.settings.Settings? = null

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> =
        syncService.listCalendars()

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            return syncService.getEvents(targetId)
        } catch (e: GoogleApiException) {
            throw mapCalendarException(targetId, e)
        }
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            syncService.syncEvent(event, targetId)
        } catch (e: GoogleApiException) {
            throw mapCalendarException(targetId, e)
        }
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            syncService.syncEvent(event, targetId)
        } catch (e: GoogleApiException) {
            throw mapCalendarException(targetId, e)
        }
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

    override suspend fun clearLocalCalendar(calendarId: String) = Unit

    override suspend fun getEventsInRange(
        start: LocalDate,
        end: LocalDate,
        calendarId: String
    ): List<Event> {
        val events = getAllEvents(calendarId)
        return eventFilter.filterByDateRange(events, start, end)
    }

    override suspend fun getEventsBySyncStatus(
        status: SyncStatus,
        calendarId: String
    ): List<Event> {
        val events = getAllEvents(calendarId)
        return eventFilter.filterBySyncStatus(events, status)
    }

    override suspend fun getIncompleteEventsBefore(
        date: LocalDate,
        calendarId: String
    ): List<Event> {
        val events = getAllEvents(calendarId)
        return eventFilter.filterIncompleteBeforeDate(events, date)
    }

    /**
     * Maps Google API exceptions to user-friendly calendar-specific exceptions.
     * Detects 404 (calendar deleted) and 403 (access revoked) and provides clear guidance.
     */
    private fun mapCalendarException(calendarId: String, e: GoogleApiException): Throwable {
        return when (e.statusCode) {
            404 -> CalendarNotFoundException(
                calendarId = calendarId,
                message = "Calendar '$calendarId' no longer exists or has been deleted on Google Calendar. " +
                        "Please re-link your calendar or use a different calendar."
            )

            403 -> CalendarNotFoundException(
                calendarId = calendarId,
                message = "No longer have access to calendar '$calendarId'. " +
                        "The calendar owner may have revoked your access."
            )

            else -> e
        }
    }
}

/**
 * Thrown when a calendar is no longer accessible on the server.
 * This can happen if the calendar was deleted or access was revoked.
 */
class CalendarNotFoundException(
    val calendarId: String,
    override val message: String
) : Exception(message)
