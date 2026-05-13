package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Implementation of [RemoteCalendarRepository] using the Google Calendar REST API.
 */
class GoogleRemoteCalendarRepository(
    private val syncService: GoogleCalendarSyncService,
    private val tokenRepository: GoogleTokenRepository,
    private val authService: GoogleAuthService
) : RemoteCalendarRepository {

    override fun getSettings(): com.russhwolf.settings.Settings? = null

    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val currentToken = tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")
        return try {
            block(currentToken)
        } catch (e: GoogleApiException) {
            if (e.statusCode == 401) {
                val refreshToken = tokenRepository.getRefreshToken() ?: throw e
                val newToken = authService.refreshAccessToken(refreshToken) ?: throw e
                tokenRepository.saveTokens(newToken, refreshToken)
                block(newToken)
            } else {
                throw e
            }
        }
    }

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> = withToken { token ->
        syncService.listCalendars(token)
    }

    override suspend fun getAllEvents(calendarId: String): List<Event> = withToken { token ->
        val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
        syncService.getEvents(token, targetId)
    }

    /**
     * Finds the 'CEF Academic' calendar ID, or creates it if it doesn't exist.
     */
    suspend fun getCEFCalendarId(): String = withToken { token ->
        val calendars = syncService.listCalendars(token)
        val cefCal = calendars.find { it.name == "CEF Academic" }
        cefCal?.id ?: syncService.createCalendar(token, "CEF Academic")
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        withToken { token ->
            val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
            
            // 1. Fetch current events for THAT specific calendar to check for overlaps
            val existingEvents = syncService.getEvents(token, targetId)

            // 2. Perform the overlap check
            val conflict = existingEvents.find { it.overlaps(event) }
            if (conflict != null) {
                throw OverlapException(existingEvent = conflict, newEvent = event)
            }
            
            // 3. Save if clear
            syncService.syncEvent(event, token, targetId)
        }
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        withToken { token ->
            val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
            syncService.syncEvent(event, token, targetId)
        }
    }

    override suspend fun deleteEvent(eventId: String, calendarId: String) {
        withToken { token ->
            val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
            try {
                syncService.deleteEvent(token, targetId, eventId)
            } catch (e: GoogleApiException) {
                if (e.statusCode != 410) throw e
            }
        }
    }

    override suspend fun hardDeleteEvent(eventId: String, calendarId: String) {
        deleteEvent(eventId, calendarId)
    }

    override suspend fun clearCalendar(calendarId: String) {
        withToken { token ->
            val targetId = if (calendarId == "default") getCEFCalendarId() else calendarId
            val events = syncService.getEvents(token, targetId)
            events.forEach { event ->
                event.id?.let { id ->
                    try {
                        syncService.deleteEvent(token, targetId, id)
                    } catch (e: GoogleApiException) {
                        if (e.statusCode != 410) throw e
                    }
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
