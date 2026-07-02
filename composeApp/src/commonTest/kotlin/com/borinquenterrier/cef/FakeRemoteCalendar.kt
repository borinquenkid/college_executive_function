package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * In-memory stand-in for a Google Calendar in headless scenario tests. Idempotent by event id
 * (a re-push of the same id updates in place — mirroring the real idempotent syncEvent), seedable
 * to represent any intermediate remote state, and fault-injectable to reproduce failure states
 * (e.g. a rate-limited delete mid-clear).
 */
class FakeRemoteCalendar : RemoteCalendarRepository {

    private val store = LinkedHashMap<String, Event>()
    private var idSeq = 0

    /** Hook invoked before each delete; throw from it to simulate a remote failure (e.g. 403). */
    var beforeDelete: (eventId: String) -> Unit = {}

    /** Hook invoked before each save; throw from it to simulate a remote failure. */
    var beforeSave: (event: Event) -> Unit = {}

    // ── seeding / inspection (test-only) ──────────────────────────────────────
    fun seed(events: List<Event>) = events.forEach { put(it) }
    fun events(): List<Event> = store.values.toList()
    fun size(): Int = store.size

    private fun put(event: Event) {
        val id = event.id ?: "remote-gen-${idSeq++}"
        store[id] = if (event.id == id) event else event.withId(id)
    }

    private fun Event.withId(id: String): Event = when (this) {
        is TimeEvent -> copy(id = id)
        is DayEvent -> copy(id = id)
    }

    // ── RemoteCalendarRepository ──────────────────────────────────────────────
    override fun getSettings(): com.russhwolf.settings.Settings? = null

    override suspend fun getAllEvents(calendarId: String): List<Event> = store.values.toList()

    override suspend fun saveEvent(event: Event, calendarId: String) {
        beforeSave(event)
        put(event) // idempotent upsert by id
    }

    override suspend fun updateEvent(event: Event, calendarId: String) = saveEvent(event, calendarId)

    override suspend fun deleteEvent(eventId: String, calendarId: String) {
        beforeDelete(eventId)
        store.remove(eventId)
    }

    override suspend fun hardDeleteEvent(eventId: String, calendarId: String) = deleteEvent(eventId, calendarId)

    override suspend fun clearCalendar(calendarId: String) {
        store.keys.toList().forEach { deleteEvent(it, calendarId) }
    }

    override suspend fun clearLocalCalendar(calendarId: String) = Unit

    override suspend fun getAvailableCalendars(): List<RemoteCalendarMetadata> =
        listOf(RemoteCalendarMetadata("default", "Scenario Calendar"))

    override suspend fun getEventsInRange(start: LocalDate, end: LocalDate, calendarId: String): List<Event> =
        store.values.filter { it.date in start..end }

    override suspend fun getEventsBySyncStatus(status: SyncStatus, calendarId: String): List<Event> =
        store.values.filter { it.syncStatus == status }

    override suspend fun getIncompleteEventsBefore(date: LocalDate, calendarId: String): List<Event> =
        store.values.filter { it.completionStatus == CompletionStatus.INCOMPLETE && it.date < date }
}
