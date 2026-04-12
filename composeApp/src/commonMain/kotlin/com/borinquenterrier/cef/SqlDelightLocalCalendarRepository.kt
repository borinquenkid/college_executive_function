package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class SqlDelightLocalCalendarRepository(private val database: AppDatabase) : StudentCalendarRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        return database.appDatabaseQueries.selectAllEvents().executeAsList()
            .filter { it.syncStatus != SyncStatus.DELETED_LOCALLY.name }
            .map { entity ->
                mapEntityToEvent(entity)
            }
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        // 1. Perform overlap check locally
        val existingEvents = getAllEvents(calendarId)
        val conflict = existingEvents.find { it.overlaps(event) }
        if (conflict != null) {
            throw OverlapException(existingEvent = conflict, newEvent = event)
        }

        // 2. Insert into database
        updateEvent(event, calendarId)
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        val recurrenceStr = when (event) {
            is TimeEvent -> event.recurrence?.let { r -> json.encodeToString(r) }
            is DayEvent -> event.recurrence?.let { r -> json.encodeToString(r) }
        }

        database.appDatabaseQueries.insertEvent(
            id = event.id ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString(),
            title = event.title,
            source = event.source.name,
            category = event.category.name,
            date = when(event) {
                is TimeEvent -> event.date.toString()
                is DayEvent -> event.date.toString()
            },
            startTime = (event as? TimeEvent)?.startTime?.toString(),
            endTime = (event as? TimeEvent)?.endTime?.toString(),
            recurrence = recurrenceStr,
            syncStatus = event.syncStatus.name,
            updatedAt = event.updatedAt
        )
    }

    override suspend fun deleteEvent(eventId: String, calendarId: String) {
        database.appDatabaseQueries.markAsDeleted(
            updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            id = eventId
        )
    }

    override suspend fun hardDeleteEvent(eventId: String, calendarId: String) {
        database.appDatabaseQueries.deleteEvent(eventId)
    }

    override suspend fun getEventsInRange(start: LocalDate, end: LocalDate, calendarId: String): List<Event> {
        return database.appDatabaseQueries.selectEventsInRange(start.toString(), end.toString())
            .executeAsList()
            .map { mapEntityToEvent(it) }
    }

    override suspend fun getEventsBySyncStatus(status: SyncStatus, calendarId: String): List<Event> {
        return database.appDatabaseQueries.selectBySyncStatus(status.name)
            .executeAsList()
            .map { mapEntityToEvent(it) }
    }

    private fun mapEntityToEvent(entity: com.borinquenterrier.cef.db.EventEntity): Event {
        val source = EventSource.valueOf(entity.source)
        val category = AcademicCategory.valueOf(entity.category)
        val syncStatus = SyncStatus.valueOf(entity.syncStatus)
        val date = LocalDate.parse(entity.date)
        val recurrence = entity.recurrence?.let { json.decodeFromString<Recurrence>(it) }

        return if (entity.startTime != null && entity.endTime != null) {
            TimeEvent(
                id = entity.id,
                title = entity.title,
                source = source,
                category = category,
                syncStatus = syncStatus,
                updatedAt = entity.updatedAt,
                date = date,
                startTime = LocalTime.parse(entity.startTime),
                endTime = LocalTime.parse(entity.endTime),
                recurrence = recurrence
            )
        } else {
            DayEvent(
                id = entity.id,
                title = entity.title,
                source = source,
                category = category,
                syncStatus = syncStatus,
                updatedAt = entity.updatedAt,
                date = date,
                recurrence = recurrence
            )
        }
    }
}
