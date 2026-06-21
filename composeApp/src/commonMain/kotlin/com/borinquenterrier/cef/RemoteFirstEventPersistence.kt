package com.borinquenterrier.cef

class RemoteFirstEventPersistence(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val syncGate: SyncGate,
    private val logger: Logger?,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository?
) {
    private val tag = "RemoteFirstEventPersistence"

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
        logOverrideIfStudyBlockMoved(event, calendarId)
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

    suspend fun delete(eventId: String, calendarId: String) {
        val event = localRepo.getAllEvents(calendarId).find { it.id == eventId }
        if (event != null && event.category == AcademicCategory.STUDY_BLOCK) {
            userPreferenceMemoryRepository?.logOverride(OverrideAction.DELETE, event)
        }
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

    suspend fun retryLocalOnly(calendarId: String) {
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

    private suspend fun logOverrideIfStudyBlockMoved(event: Event, calendarId: String) {
        val original = localRepo.getAllEvents(calendarId).find { it.id == event.id }
        if (original != null && original.category == AcademicCategory.STUDY_BLOCK) {
            val hasMoved = original.date != event.date ||
                (original is TimeEvent && event is TimeEvent &&
                    (original.startTime != event.startTime || original.endTime != event.endTime))
            if (hasMoved) {
                userPreferenceMemoryRepository?.logOverride(OverrideAction.MOVE, original)
            }
        }
    }
}
