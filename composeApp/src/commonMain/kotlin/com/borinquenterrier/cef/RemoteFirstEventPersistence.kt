package com.borinquenterrier.cef

class RemoteFirstEventPersistence(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val syncGate: SyncGate,
    private val logger: Logger?,
    userPreferenceMemoryRepository: UserPreferenceMemoryRepository?
) {
    private val tag = "RemoteFirstEventPersistence"
    private val overrideLogger = StudyBlockOverrideLogger(localRepo, userPreferenceMemoryRepository)
    private val writer = RemoteFirstWriter(localRepo, remoteRepo, syncGate, logger)
    private val deleter = EventDeleter(localRepo, remoteRepo, syncGate, overrideLogger, logger)
    private val retrier = LocalOnlyRetrier(localRepo, remoteRepo, syncGate, logger)

    suspend fun save(event: Event, calendarId: String) = writer.save(event, calendarId)

    suspend fun update(event: Event, calendarId: String) {
        overrideLogger.checkMove(event, calendarId)
        writer.update(event, calendarId)
    }

    suspend fun delete(eventId: String, calendarId: String) = deleter.delete(eventId, calendarId)

    suspend fun retryLocalOnly(calendarId: String) = retrier.retry(calendarId)

    suspend fun reset(calendarId: String) {
        localRepo.clearLocalCalendar(calendarId)
        if (syncGate.isLive()) {
            try {
                remoteRepo.clearCalendar(calendarId)
            } catch (e: Exception) {
                logger?.e(tag, "Remote calendar clear failed, local reset still complete", e)
            }
        }
    }
}
