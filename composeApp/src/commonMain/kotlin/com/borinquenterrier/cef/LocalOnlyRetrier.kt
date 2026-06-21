package com.borinquenterrier.cef

class LocalOnlyRetrier(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val syncGate: SyncGate,
    private val logger: Logger?
) {
    private val tag = "LocalOnlyRetrier"

    suspend fun retry(calendarId: String) {
        if (!syncGate.isLive()) return
        val pending = localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, calendarId)
        for (event in pending) {
            try {
                remoteRepo.saveEvent(event, calendarId)
                localRepo.updateEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
                logger?.d(tag, "Retried LOCAL_ONLY to remote: ${event.title}")
            } catch (e: Exception) {
                logger?.d(tag, "Retry failed, keeping LOCAL_ONLY: ${event.title}")
            }
        }
    }
}
