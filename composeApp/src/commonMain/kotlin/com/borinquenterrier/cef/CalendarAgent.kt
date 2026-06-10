package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Orchestrator that manages synchronization between Local (Offline) and Remote (Gold Standard) repositories.
 *
 * Owns CRUD operations with remote-first persistence; delegates the actual two-way
 * sync reconciliation to [SyncNegotiator] (builds proposals) and [SyncNegotiationApplier]
 * (applies them).
 */
class CalendarAgent(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null,
    private val preferencesRepository: PreferencesRepository? = null
) {
    private val negotiator =
        SyncNegotiator(localRepo, remoteRepo, userPreferenceMemoryRepository, preferencesRepository)
    private val negotiationApplier =
        SyncNegotiationApplier(localRepo, remoteRepo, logger, userPreferenceMemoryRepository)

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
        if (isLiveSyncEnabled()) {
            // Attempt to save to remote first (Gold Standard)
            remoteRepo.saveEvent(event, calendarId)

            // If remote success, save locally as SYNCED
            localRepo.saveEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
        } else {
            // In test profile, skip remote and save locally only
            saveEventLocally(event, calendarId)
        }
    }

    /**
     * Updates an existing event. Saves to Remote first (unless in test profile).
     * If successful, saves/updates locally as SYNCED.
     */
    suspend fun updateEvent(event: Event, calendarId: String = "default") {
        val original = localRepo.getAllEvents(calendarId).find { it.id == event.id }
        if (original != null && original.category == AcademicCategory.STUDY_BLOCK) {
            val hasMoved =
                original.date != event.date || (original is TimeEvent && event is TimeEvent && (original.startTime != event.startTime || original.endTime != event.endTime))
            if (hasMoved) {
                userPreferenceMemoryRepository?.logOverride(OverrideAction.MOVE, original)
            }
        }

        if (isLiveSyncEnabled()) {
            remoteRepo.saveEvent(event, calendarId)
            localRepo.updateEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
        } else {
            localRepo.updateEvent(event, calendarId)
        }
    }

    /**
     * Explicitly saves an event to the local database only.
     * Used for offline support or when the user hasn't linked Workspace.
     */
    suspend fun saveEventLocally(event: Event, calendarId: String = "default") {
        localRepo.saveEvent(event.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
    }

    /**
     * Deletes an event. Soft-deletes locally and attempts remote delete if online.
     */
    suspend fun deleteEvent(eventId: String, calendarId: String = "default") {
        val event = localRepo.getAllEvents(calendarId).find { it.id == eventId }
        if (event != null && event.category == AcademicCategory.STUDY_BLOCK) {
            userPreferenceMemoryRepository?.logOverride(OverrideAction.DELETE, event)
        }

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
     * Checks remote for changes and compiles a negotiation proposal if there are conflicts or shifts.
     */
    suspend fun checkSyncProposals(calendarId: String = "default"): SyncNegotiation =
        negotiator.buildNegotiation(calendarId)

    /**
     * Applies the sync negotiation results to the local and remote repositories.
     */
    suspend fun applySyncNegotiation(negotiation: SyncNegotiation, calendarId: String = "default") =
        negotiationApplier.apply(negotiation, calendarId)

    /**
     * Performs a full synchronization using Remote as the Gold Standard.
     */
    suspend fun synchronize(calendarId: String = "default") {
        try {
            val negotiation = checkSyncProposals(calendarId)
            applySyncNegotiation(negotiation, calendarId)
        } catch (e: Exception) {
            // Sync failed (likely offline), keep working with local data
            throw e
        }
    }

    /**
     * Retrieves incomplete events before a given date.
     */
    suspend fun getIncompleteEventsBefore(
        date: LocalDate,
        calendarId: String = "default"
    ): List<Event> {
        return localRepo.getIncompleteEventsBefore(date, calendarId)
    }

    private suspend fun isLiveSyncEnabled(): Boolean =
        (localRepo.getSettings()?.getString("run_profile", "local") ?: "local") != "test"
}
