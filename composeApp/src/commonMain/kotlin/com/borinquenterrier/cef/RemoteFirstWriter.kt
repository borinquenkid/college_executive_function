package com.borinquenterrier.cef

class RemoteFirstWriter(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val syncGate: SyncGate,
    private val logger: Logger?
) {
    private val tag = "RemoteFirstWriter"

    suspend fun save(event: Event, calendarId: String) {
        if (syncGate.isLive()) {
            try {
                remoteRepo.saveEvent(event, calendarId)
                localRepo.saveEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
            } catch (e: CalendarNotFoundException) {
                throw e
            } catch (e: Exception) {
                logger?.e(tag, "Remote save failed, falling back to local-only save", e)
                localRepo.saveEvent(event.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
                throw RemoteSyncFailedException("Could not reach Google Calendar: ${e.message}", e)
            }
        } else {
            localRepo.saveEvent(event.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
        }
    }

    suspend fun update(event: Event, calendarId: String) {
        if (syncGate.isLive()) {
            try {
                remoteRepo.saveEvent(event, calendarId)
                localRepo.updateEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
            } catch (e: CalendarNotFoundException) {
                throw e
            } catch (e: Exception) {
                logger?.e(tag, "Remote update failed, falling back to local-only update", e)
                localRepo.updateEvent(event.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
            }
        } else {
            localRepo.updateEvent(event, calendarId)
        }
    }
}
