package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Orchestrator that manages synchronization between Local (Offline) and Remote (Gold Standard) repositories.
 */
class CalendarAgent(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository
) {

    /**
     * Retrieves the consolidated list of events.
     * Prioritizes local for speed, but triggers a background sync.
     */
    suspend fun getEvents(calendarId: String = "default"): List<Event> {
        return localRepo.getAllEvents(calendarId)
    }

    /**
     * Saves a new event. Attempts to save to Remote first (unless in test profile). 
     * If successful, saves to Local as SYNCED.
     * If Remote fails, this method now throws the exception so the UI can handle it.
     */
    suspend fun saveEvent(event: Event, calendarId: String = "default") {
        val runProfile = localRepo.getSettings()?.getString("run_profile", "local") ?: "local"
        
        if (runProfile != "test") {
            // Attempt to save to remote first (Gold Standard)
            remoteRepo.saveEvent(event, calendarId)
            
            // If remote success, save locally as SYNCED
            localRepo.saveEvent(
                when (event) {
                    is TimeEvent -> event.copy(syncStatus = SyncStatus.SYNCED)
                    is DayEvent -> event.copy(syncStatus = SyncStatus.SYNCED)
                }, 
                calendarId
            )
        } else {
            // In test profile, skip remote and save locally only
            saveEventLocally(event, calendarId)
        }
    }

    /**
     * Explicitly saves an event to the local database only. 
     * Used for offline support or when the user hasn't linked Workspace.
     */
    suspend fun saveEventLocally(event: Event, calendarId: String = "default") {
        localRepo.saveEvent(
            when (event) {
                is TimeEvent -> event.copy(syncStatus = SyncStatus.LOCAL_ONLY)
                is DayEvent -> event.copy(syncStatus = SyncStatus.LOCAL_ONLY)
            },
            calendarId
        )
    }

    /**
     * Deletes an event. Soft-deletes locally and attempts remote delete if online.
     */
    suspend fun deleteEvent(eventId: String, calendarId: String = "default") {
        localRepo.deleteEvent(eventId, calendarId)
        try {
            remoteRepo.deleteEvent(eventId, calendarId)
            // If remote success, hard delete locally
            localRepo.hardDeleteEvent(eventId, calendarId)
        } catch (e: Exception) {
            // Stay as DELETED_LOCALLY for later sync
        }
    }

    /**
     * Performs a full synchronization using Remote as the Gold Standard.
     */
    suspend fun synchronize(calendarId: String = "default") {
        try {
            val localEvents = localRepo.getAllEvents(calendarId)
            val deletedLocally = localRepo.getEventsBySyncStatus(SyncStatus.DELETED_LOCALLY, calendarId)

            // 1. Handle Local Deletions: PUSH soft-deletes to Remote
            deletedLocally.forEach { local ->
                local.id?.let { id ->
                    try {
                        remoteRepo.deleteEvent(id, calendarId)
                        localRepo.hardDeleteEvent(id, calendarId)
                    } catch (e: Exception) {
                        // Network failure, keep as DELETED_LOCALLY to try again later
                    }
                }
            }

            // 2. Handle Local Creations: PUSH new events to Remote
            localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, calendarId).forEach { local ->
                try {
                    remoteRepo.saveEvent(local, calendarId)
                    localRepo.hardDeleteEvent(local.id ?: "", calendarId)
                } catch (e: Exception) {
                    // Network failure or overlap on remote, keep as LOCAL_ONLY
                }
            }

            // 3. Remote is Gold Standard: Fetch final state
            val remoteEvents = remoteRepo.getAllEvents(calendarId)

            // 4. Remote is Gold Standard: Any local SYNCED event missing from Remote is deleted.
            localRepo.getAllEvents(calendarId).filter { it.syncStatus == SyncStatus.SYNCED }.forEach { local ->
                if (remoteEvents.none { it.id == local.id }) {
                    local.id?.let { localRepo.hardDeleteEvent(it, calendarId) }
                }
            }

            // 5. Remote is Gold Standard: Upsert ALL remote events to Local
            remoteEvents.forEach { remote ->
                localRepo.updateEvent(
                    when (remote) {
                        is TimeEvent -> remote.copy(syncStatus = SyncStatus.SYNCED)
                        is DayEvent -> remote.copy(syncStatus = SyncStatus.SYNCED)
                    },
                    calendarId
                )
            }

        } catch (e: Exception) {
            // Sync failed (likely offline), keep working with local data
            throw e
        }
    }
}
