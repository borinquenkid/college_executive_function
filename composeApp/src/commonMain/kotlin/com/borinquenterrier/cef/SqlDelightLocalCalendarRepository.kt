package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json

class SqlDelightLocalCalendarRepository(
    private val database: AppDatabase,
    private val settings: Settings? = null
) : StudentCalendarRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getSettings(): Settings? = settings

    override suspend fun getAllEvents(calendarId: String): List<Event> {
        return database.appDatabaseQueries.selectAllEvents().executeAsList()
            .filter { it.syncStatus != SyncStatus.DELETED_LOCALLY.name }
            .map { entity ->
                mapEntityToEvent(entity)
            }
    }

    override suspend fun saveEvent(event: Event, calendarId: String) {
        updateEvent(event, calendarId)
    }

    override suspend fun updateEvent(event: Event, calendarId: String) {
        val recurrenceStr = when (event) {
            is TimeEvent -> event.recurrence?.let { r -> json.encodeToString(r) }
            is DayEvent -> event.recurrence?.let { r -> json.encodeToString(r) }
        }

        database.appDatabaseQueries.insertEvent(
            id = event.id ?: "${
                Clock.System.now().toEpochMilliseconds()
            }-${(1000..9999).random()}",
            title = event.title,
            source = event.source.name,
            category = event.category.name,
            date = when (event) {
                is TimeEvent -> event.date.toString()
                is DayEvent -> event.date.toString()
            },
            startTime = if (event is TimeEvent) event.startTime.toString() else null,
            endTime = if (event is TimeEvent) event.endTime.toString() else null,
            recurrence = recurrenceStr,
            syncStatus = event.syncStatus.name,
            updatedAt = event.updatedAt,
            studyPlanStart = event.studyPlanStart,
            gradeWeight = event.gradeWeight?.toDouble(),
            completionStatus = event.completionStatus.name,
            warning = event.warning
        )
    }

    override suspend fun deleteEvent(eventId: String, calendarId: String) {
        database.appDatabaseQueries.markAsDeleted(
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            id = eventId
        )
    }

    override suspend fun hardDeleteEvent(eventId: String, calendarId: String) {
        database.appDatabaseQueries.deleteEvent(eventId)
    }

    override suspend fun clearLocalCalendar(calendarId: String) {
        database.appDatabaseQueries.deleteAllEvents()
    }

    override suspend fun getEventsInRange(
        start: LocalDate,
        end: LocalDate,
        calendarId: String
    ): List<Event> {
        return database.appDatabaseQueries.selectEventsInRange(start.toString(), end.toString())
            .executeAsList()
            .map { mapEntityToEvent(it) }
    }

    override suspend fun getEventsBySyncStatus(
        status: SyncStatus,
        calendarId: String
    ): List<Event> {
        return database.appDatabaseQueries.selectBySyncStatus(status.name)
            .executeAsList()
            .map { mapEntityToEvent(it) }
    }

    override suspend fun getIncompleteEventsBefore(
        date: LocalDate,
        calendarId: String
    ): List<Event> {
        return database.appDatabaseQueries.selectIncompleteBeforeDate(date.toString())
            .executeAsList()
            .map { mapEntityToEvent(it) }
    }

    private fun mapEntityToEvent(entity: com.borinquenterrier.cef.db.EventEntity): Event {
        val source = EventSource.valueOf(entity.source)
        val category = AcademicCategory.valueOf(entity.category)
        val syncStatus = SyncStatus.valueOf(entity.syncStatus)
        val date = LocalDate.parse(entity.date)
        val recurrence = entity.recurrence?.let { json.decodeFromString<Recurrence>(it) }
        val completionStatus = try {
            CompletionStatus.valueOf(entity.completionStatus)
        } catch (e: IllegalArgumentException) {
            CompletionStatus.INCOMPLETE
        }

        return if (entity.startTime != null && entity.endTime != null) {
            TimeEvent(
                id = entity.id,
                title = entity.title,
                source = source,
                category = category,
                syncStatus = syncStatus,
                updatedAt = entity.updatedAt,
                studyPlanStart = entity.studyPlanStart,
                gradeWeight = entity.gradeWeight?.toFloat(),
                completionStatus = completionStatus,
                date = date,
                startTime = LocalTime.parse(entity.startTime),
                endTime = LocalTime.parse(entity.endTime),
                recurrence = recurrence,
                warning = entity.warning
            )
        } else {
            DayEvent(
                id = entity.id,
                title = entity.title,
                source = source,
                category = category,
                syncStatus = syncStatus,
                updatedAt = entity.updatedAt,
                studyPlanStart = entity.studyPlanStart,
                gradeWeight = entity.gradeWeight?.toFloat(),
                completionStatus = completionStatus,
                date = date,
                recurrence = recurrence,
                warning = entity.warning
            )
        }
    }
}
