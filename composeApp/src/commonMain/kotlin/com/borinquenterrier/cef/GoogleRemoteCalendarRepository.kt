package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Lightweight facade coordinating calendar operations.
 * Delegates to specialized services for ID resolution, conflict detection, and filtering.
 */
class GoogleRemoteCalendarRepository(
    private val syncService: GoogleCalendarSyncService,
    private val calendarIdResolver: CalendarIdResolver,
    private val conflictDetector: EventConflictDetector
) : RemoteCalendarRepository {
    private val eventQuery = EventQueryService(::getAllEvents)

    override fun getSettings(): com.russhwolf.settings.Settings? = null

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> =
        try {
            syncService.listCalendars()
        } catch (e: GoogleApiException) {
            throw e.toCalendarException("calendarList")
        }

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            return syncService.getEvents(targetId)
        } catch (e: GoogleApiException) {
            throw e.toCalendarException(targetId)
        }
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            syncService.syncEvent(event, targetId)
        } catch (e: GoogleApiException) {
            throw e.toCalendarException(targetId)
        }
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        val targetId = calendarIdResolver.resolveCalendarId(calendarId)
        try {
            syncService.syncEvent(event, targetId)
        } catch (e: GoogleApiException) {
            throw e.toCalendarException(targetId)
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
            val id = event.id ?: return@forEach
            try {
                syncService.deleteEvent(targetId, id)
            } catch (e: GoogleApiException) {
                if (e.statusCode != 410) throw e
            }
        }
    }

    override suspend fun clearLocalCalendar(calendarId: String) = Unit

    override suspend fun getEventsInRange(start: LocalDate, end: LocalDate, calendarId: String): List<Event> =
        eventQuery.getEventsInRange(calendarId, start, end)

    override suspend fun getEventsBySyncStatus(status: SyncStatus, calendarId: String): List<Event> =
        eventQuery.getEventsBySyncStatus(calendarId, status)

    override suspend fun getIncompleteEventsBefore(date: LocalDate, calendarId: String): List<Event> =
        eventQuery.getIncompleteEventsBefore(calendarId, date)
}

/**
 * Thrown when a calendar is no longer accessible on the server.
 * This can happen if the calendar was deleted or access was revoked.
 */
class CalendarNotFoundException(
    val calendarId: String,
    override val message: String
) : Exception(message)
