package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Implementation of [RemoteCalendarRepository] using the Google Calendar REST API.
 */
class GoogleRemoteCalendarRepository(
    private val syncService: GoogleCalendarSyncService,
    private val tokenRepository: GoogleTokenRepository
) : RemoteCalendarRepository {

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> {
        val token = tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")
        return syncService.listCalendars(token)
    }

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        val token = tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")
        val targetId = if (calendarId == "default") "primary" else calendarId
        return syncService.getEvents(token, targetId)
    }

    /**
     * Finds the 'CEF Academic' calendar ID, or creates it if it doesn't exist.
     */
    suspend fun getCEFCalendarId(): String {
        val token = tokenRepository.getAccessToken() ?: throw Exception("Not authenticated")
        val calendars = syncService.listCalendars(token)
        val cefCal = calendars.find { it.name == "CEF Academic" }

        return cefCal?.id ?: syncService.createCalendar(token, "CEF Academic")
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        val token = tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")

        // If 'default' is passed, we use the dedicated CEF calendar
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId

        // 1. Fetch current events for THAT specific calendar to check for overlaps
        val existingEvents = getAllEvents(targetId)

        // 2. Perform the overlap check
        val conflict = existingEvents.find { it.overlaps(event) }
        if (conflict != null) {
            throw OverlapException(existingEvent = conflict, newEvent = event)
        }
        
        // 3. Save if clear
        syncService.syncEvent(event, token, targetId)
    }

    override suspend fun clearCalendar(calendarId: String) {
        val token = tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")
        val targetId = if (calendarId == "default") "primary" else calendarId
        val events = getAllEvents(targetId)
        events.forEach { event ->
            event.id?.let { id ->
                try {
                    syncService.deleteEvent(token, targetId, id)
                } catch (e: GoogleApiException) {
                    if (e.statusCode != 410) throw e // Ignore if already deleted
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
}
