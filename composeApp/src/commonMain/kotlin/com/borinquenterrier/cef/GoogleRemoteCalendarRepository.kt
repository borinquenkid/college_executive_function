package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Implementation of [RemoteCalendarRepository] using the Google Calendar REST API.
 */
class GoogleRemoteCalendarRepository(
    private val syncService: GoogleCalendarSyncService
) : RemoteCalendarRepository {

    override fun getSettings(): com.russhwolf.settings.Settings? = null

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> =
        syncService.listCalendars()

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
        return syncService.getEvents(targetId)
    }

    /**
     * Finds the 'CEF Academic' calendar ID, or creates it if it doesn't exist.
     */
    suspend fun getCEFCalendarId(): String {
        val calendars = syncService.listCalendars()
        val cefCal = calendars.find { it.name == "CEF Academic" }
        return cefCal?.id ?: syncService.createCalendar("CEF Academic")
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
        
        // 1. Fetch current events for THAT specific calendar to check for overlaps
        val existingEvents = syncService.getEvents(targetId)

        // 2. Perform the overlap check
        val conflict = existingEvents.find { it.id != event.id && it.overlaps(event) }
        if (conflict != null) {
            throw OverlapException(existingEvent = conflict, newEvent = event)
        }
        
        // 3. Save if clear
        syncService.syncEvent(event, targetId)
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
        syncService.syncEvent(event, targetId)
    }

    override suspend fun deleteEvent(eventId: String, calendarId: String) {
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
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
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
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
        return getAllEvents(calendarId).filter { event ->
            val date = when (event) {
                is TimeEvent -> event.date
                is DayEvent -> event.date
            }
            date in start..end
        }
    }

    override suspend fun getEventsBySyncStatus(status: SyncStatus, calendarId: String): List<Event> {
        return if (status == SyncStatus.SYNCED) getAllEvents(calendarId) else emptyList()
    }
}
