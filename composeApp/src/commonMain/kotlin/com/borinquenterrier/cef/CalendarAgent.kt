package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
    private val tag = "CalendarAgent"

    private val _resetVersion = MutableStateFlow(0)
    val resetVersion: StateFlow<Int> = _resetVersion.asStateFlow()

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
        val event = repairEndTime(event)
        event.validate()
        if (isLiveSyncEnabled() && isGoogleLinked()) {
            try {
                // Attempt to save to remote first (Gold Standard)
                remoteRepo.saveEvent(event, calendarId)
                // If remote success, save locally as SYNCED
                localRepo.saveEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
            } catch (e: CalendarNotFoundException) {
                // Calendar was deleted — surface to CalendarPusher so the user sees an error.
                throw e
            } catch (e: Exception) {
                logger?.e(tag, "Remote save failed, falling back to local-only save", e)
                saveEventLocally(event, calendarId)
                // Event preserved locally; throw so callers can surface the remote failure.
                throw RemoteSyncFailedException("Could not reach Google Calendar: ${e.message}", e)
            }
        } else {
            // In test profile or unlinked, skip remote and save locally only
            saveEventLocally(event, calendarId)
        }
    }

    /**
     * Updates an existing event. Saves to Remote first (unless in test profile).
     * If successful, saves/updates locally as SYNCED.
     */
    suspend fun updateEvent(event: Event, calendarId: String = "default") {
        val event = repairEndTime(event)
        event.validate()
        val original = localRepo.getAllEvents(calendarId).find { it.id == event.id }
        if (original != null && original.category == AcademicCategory.STUDY_BLOCK) {
            val hasMoved =
                original.date != event.date || (original is TimeEvent && event is TimeEvent && (original.startTime != event.startTime || original.endTime != event.endTime))
            if (hasMoved) {
                userPreferenceMemoryRepository?.logOverride(OverrideAction.MOVE, original)
            }
        }

        if (isLiveSyncEnabled() && isGoogleLinked()) {
            try {
                remoteRepo.saveEvent(event, calendarId)
                localRepo.updateEvent(event.withSyncStatus(SyncStatus.SYNCED), calendarId)
            } catch (e: Exception) {
                logger?.e(tag, "Remote update failed, falling back to local-only update", e)
                localRepo.updateEvent(event.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
            }
        } else {
            localRepo.updateEvent(event, calendarId)
        }
    }

    /**
     * Explicitly saves an event to the local database only.
     * Used for offline support or when the user hasn't linked Workspace.
     */
    suspend fun saveEventLocally(event: Event, calendarId: String = "default") {
        val event = repairEndTime(event)
        event.validate()
        localRepo.saveEvent(event.withSyncStatus(SyncStatus.LOCAL_ONLY), calendarId)
    }

    /** Hard-deletes a LOCAL_ONLY event that was never synced to remote. Safe to call before re-pushing. */
    suspend fun hardDeleteLocalOnly(id: String, calendarId: String) {
        localRepo.hardDeleteEvent(id, calendarId)
    }

    /**
     * Retries all LOCAL_ONLY events by pushing them to the remote calendar.
     * Called automatically at app startup when Google is linked, so events that
     * failed during a previous session (e.g., due to a network blip) are flushed
     * without requiring the user to manually re-push.
     *
     * On success each event is re-saved locally as SYNCED.
     * On failure the event stays LOCAL_ONLY for the next retry.
     */
    suspend fun retryLocalOnly(calendarId: String = "default") {
        if (!isLiveSyncEnabled() || !isGoogleLinked()) return
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

    suspend fun deleteEvent(eventId: String, calendarId: String = "default") {
        val event = localRepo.getAllEvents(calendarId).find { it.id == eventId }
        if (event != null && event.category == AcademicCategory.STUDY_BLOCK) {
            userPreferenceMemoryRepository?.logOverride(OverrideAction.DELETE, event)
        }

        localRepo.deleteEvent(eventId, calendarId)
        if (isLiveSyncEnabled() && isGoogleLinked()) {
            try {
                remoteRepo.deleteEvent(eventId, calendarId)
                // If remote success, hard delete locally
                localRepo.hardDeleteEvent(eventId, calendarId)
            } catch (e: Exception) {
                // Stay as DELETED_LOCALLY for later sync
                logger?.e(tag, "Remote delete failed, leaving as DELETED_LOCALLY", e)
            }
        }
    }

    suspend fun resetCalendar(calendarId: String = "default") {
        localRepo.clearLocalCalendar(calendarId)
        if (isLiveSyncEnabled() && isGoogleLinked()) {
            try {
                remoteRepo.clearCalendar(calendarId)
            } catch (e: Exception) {
                logger?.e(tag, "Remote calendar clear failed, local reset still complete", e)
            }
        }
        _resetVersion.value++
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

    private fun repairEndTime(event: Event): Event {
        if (event !is TimeEvent || event.endTime > event.startTime) return event
        val plusHourMins = event.startTime.hour * 60 + event.startTime.minute + 60
        val newEnd = if (plusHourMins < 24 * 60)
            LocalTime(plusHourMins / 60, plusHourMins % 60)
        else
            LocalTime(23, 59, 59)
        return event.copy(endTime = newEnd)
    }

    private suspend fun isLiveSyncEnabled(): Boolean =
        (localRepo.getSettings()?.getString("run_profile", "local") ?: "local") != "test"

    private fun isGoogleLinked(): Boolean {
        val settings = localRepo.getSettings() ?: return false
        return settings.getString("GOOGLE_ACCESS_TOKEN", "").isNotBlank()
    }
}
