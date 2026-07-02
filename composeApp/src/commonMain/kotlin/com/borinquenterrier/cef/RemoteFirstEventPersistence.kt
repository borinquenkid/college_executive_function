package com.borinquenterrier.cef

class RemoteFirstEventPersistence(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val syncGate: SyncGate,
    private val logger: Logger?,
    userPreferenceMemoryRepository: UserPreferenceMemoryRepository,
    clearDelayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {
    private val tag = "RemoteFirstEventPersistence"
    private val overrideLogger = StudyBlockOverrideLogger(localRepo, userPreferenceMemoryRepository)
    private val writer = RemoteFirstWriter(localRepo, remoteRepo, syncGate, logger)
    private val deleter = EventDeleter(localRepo, remoteRepo, syncGate, overrideLogger, logger)
    private val retrier = LocalOnlyRetrier(localRepo, remoteRepo, syncGate, logger)
    private val cleaner = ResilientCalendarCleaner(remoteRepo, logger, delayFn = clearDelayFn)

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
            // Resilient clear: retries rate-limited deletes and continues past failures instead of
            // aborting the whole reset on the first 403 (which left the calendar half-cleared).
            val result = cleaner.clear(calendarId)
            if (!result.allCleared) {
                logger?.e(
                    tag,
                    "Remote clear incomplete: ${result.deleted} deleted, ${result.failed} could not be removed (re-run Reset to retry)."
                )
            }
        }
    }
}
