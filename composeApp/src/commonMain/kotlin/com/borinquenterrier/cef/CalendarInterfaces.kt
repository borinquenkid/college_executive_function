package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Interface representing a source of academic events.
 */
interface CalendarInterface {
    /**
     * Retrieves all events from this specific source.
     */
    suspend fun getEvents(): List<Event>
}

/**
 * Interface for transforming raw events into categorized academic events.
 */
interface EventExtractor {
    /**
     * Transforms a list of events into a new, processed list of events.
     */
    fun extract(events: List<Event>): List<Event>
}

/**
 * Metadata for identifying an external calendar (e.g., Google 'Primary' or 'School').
 */
data class RemoteCalendarMetadata(val id: String, val name: String)

/**
 * Repository interface for managing persistent academic events.
 */
interface StudentCalendarRepository {
    /**
     * Optional settings access for run profile checks.
     */
    fun getSettings(): com.russhwolf.settings.Settings?

    /**
     * Retrieves all saved events from a specific calendar.
     */
    suspend fun getAllEvents(calendarId: String = "default"): List<Event>

    /**
     * Saves a new event to a specific calendar. 
     * Should throw an [OverlapException] if the event overlaps 
     * with an existing event in that calendar.
     */
    suspend fun saveEvent(event: Event, calendarId: String = "default")

    /**
     * Updates an event in the repository without performing overlap checks.
     * Useful for synchronization.
     */
    suspend fun updateEvent(event: Event, calendarId: String = "default")

    /**
     * Marks an event as deleted (locally or remotely).
     */
    suspend fun deleteEvent(eventId: String, calendarId: String = "default")

    /**
     * Physically removes an event from the repository.
     */
    suspend fun hardDeleteEvent(eventId: String, calendarId: String = "default")

    /**
     * Retrieves events for a specific date range from a specific calendar.
     */
    suspend fun getEventsInRange(
        start: LocalDate,
        end: LocalDate,
        calendarId: String = "default"
    ): List<Event>

    /**
     * Retrieves events by their synchronization status.
     */
    suspend fun getEventsBySyncStatus(
        status: SyncStatus,
        calendarId: String = "default"
    ): List<Event>

    /**
     * Retrieves incomplete events before the specified date.
     */
    suspend fun getIncompleteEventsBefore(
        date: LocalDate,
        calendarId: String = "default"
    ): List<Event>
}

/**
 * Implementation of StudentCalendarRepository for external services.
 * Adds the ability to discover which calendars are available.
 */
interface RemoteCalendarRepository : StudentCalendarRepository {
    /**
     * Fetches the list of calendars available for the connected account.
     */
    suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata>

    /**
     * Removes all events from a specific calendar.
     */
    suspend fun clearCalendar(calendarId: String)
}

/**
 * Exception thrown when a new event overlaps with an existing event in the repository.
 */
class OverlapException(val existingEvent: Event, val newEvent: Event) :
    Exception("Overlap detected between '${existingEvent.title}' and '${newEvent.title}'")
