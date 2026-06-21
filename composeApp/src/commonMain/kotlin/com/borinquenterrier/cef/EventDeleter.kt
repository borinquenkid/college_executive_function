package com.borinquenterrier.cef

class EventDeleter(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val syncGate: SyncGate,
    private val overrideLogger: StudyBlockOverrideLogger,
    private val logger: Logger?
) {
    private val tag = "EventDeleter"

    suspend fun delete(eventId: String, calendarId: String) {
        overrideLogger.checkDelete(eventId, calendarId)
        localRepo.deleteEvent(eventId, calendarId)
        if (syncGate.isLive()) {
            try {
                remoteRepo.deleteEvent(eventId, calendarId)
                localRepo.hardDeleteEvent(eventId, calendarId)
            } catch (e: Exception) {
                logger?.e(tag, "Remote delete failed, leaving as DELETED_LOCALLY", e)
            }
        }
    }
}
