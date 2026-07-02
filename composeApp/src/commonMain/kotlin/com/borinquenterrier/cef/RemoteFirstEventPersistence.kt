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
        clearLocal(calendarId)
        clearRemote(calendarId)
    }

    /** Clears the local calendar (atomic) — the UI can refresh off this immediately. */
    suspend fun clearLocal(calendarId: String) = localRepo.clearLocalCalendar(calendarId)

    /**
     * Clears the remote calendar resiliently (retries rate-limited deletes, continues past failures
     * instead of aborting on the first 403). Deletes one at a time, so callers should refresh the UI
     * off the local clear first and run this in the background.
     */
    suspend fun clearRemote(calendarId: String) {
        if (syncGate.isLive()) {
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
