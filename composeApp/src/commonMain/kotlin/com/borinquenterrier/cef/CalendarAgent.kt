package com.borinquenterrier.cef

import kotlinx.datetime.LocalDate

/**
 * Orchestrator that manages synchronization between Local (Offline) and Remote (Gold Standard) repositories.
 */
class CalendarAgent(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null,
    private val preferencesRepository: PreferencesRepository? = null
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
     * Updates an existing event. Saves to Remote first (unless in test profile).
     * If successful, saves/updates locally as SYNCED.
     */
    suspend fun updateEvent(event: Event, calendarId: String = "default") {
        val original = localRepo.getAllEvents(calendarId).find { it.id == event.id }
        if (original != null && original.category == AcademicCategory.STUDY_BLOCK) {
            val hasMoved = original.date != event.date || (original is TimeEvent && event is TimeEvent && (original.startTime != event.startTime || original.endTime != event.endTime))
            if (hasMoved) {
                userPreferenceMemoryRepository?.logOverride(OverrideAction.MOVE, original)
            }
        }

        val runProfile = localRepo.getSettings()?.getString("run_profile", "local") ?: "local"
        if (runProfile != "test") {
            remoteRepo.saveEvent(event, calendarId)
            localRepo.updateEvent(
                when (event) {
                    is TimeEvent -> event.copy(syncStatus = SyncStatus.SYNCED)
                    is DayEvent -> event.copy(syncStatus = SyncStatus.SYNCED)
                },
                calendarId
            )
        } else {
            localRepo.updateEvent(event, calendarId)
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

    private suspend fun pushLocalChanges(calendarId: String) {
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
    }

    /**
     * Checks remote for changes and compiles a negotiation proposal if there are conflicts or shifts.
     */
    suspend fun checkSyncProposals(calendarId: String = "default"): SyncNegotiation {
        // First push local additions and deletions to Remote so Remote is updated
        pushLocalChanges(calendarId)

        val localEvents = localRepo.getAllEvents(calendarId)
        val remoteEvents = try {
            remoteRepo.getAllEvents(calendarId)
        } catch (e: Exception) {
            // If offline, return empty negotiation
            return SyncNegotiation(emptyList(), emptyList(), emptyList())
        }

        val remoteUpdates = mutableListOf<Event>()
        val directConflicts = mutableListOf<SyncProposal.DirectConflict>()

        remoteEvents.forEach { remote ->
            val local = localEvents.find { it.id == remote.id }
            if (local == null) {
                remoteUpdates.add(remote)
            } else if (local.syncStatus == SyncStatus.SYNCED && local.updatedAt != remote.updatedAt) {
                remoteUpdates.add(remote)
                val hasFieldDiff = local.title != remote.title || local.date != remote.date ||
                        (local is TimeEvent && remote is TimeEvent && (local.startTime != remote.startTime || local.endTime != remote.endTime))
                if (hasFieldDiff) {
                    directConflicts.add(SyncProposal.DirectConflict(local, remote))
                }
            }
        }

        val deletedLocalIds = localRepo.getAllEvents(calendarId)
            .filter { it.syncStatus == SyncStatus.SYNCED }
            .filter { local -> remoteEvents.none { it.id == local.id } }
            .mapNotNull { it.id }

        val nonStudyBlocks = localEvents.filter { it.category != AcademicCategory.STUDY_BLOCK && it.id !in deletedLocalIds }
        val updatedNonStudyBlocks = nonStudyBlocks.map { local ->
            remoteUpdates.find { it.id == local.id } ?: local
        }
        val newRemoteNonStudyBlocks = remoteUpdates.filter { remote ->
            remote.category != AcademicCategory.STUDY_BLOCK && localEvents.none { it.id == remote.id }
        }

        val proposedBaseCalendar = updatedNonStudyBlocks + newRemoteNonStudyBlocks
        val localStudyBlocks = localEvents.filter { it.category == AcademicCategory.STUDY_BLOCK }
        val proposals = mutableListOf<SyncProposal>()
        proposals.addAll(directConflicts)

        val resolvedStudyBlocks = mutableListOf<Event>()

        val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
        val userConstraints = userPreferenceMemoryRepository?.getDerivedConstraints() ?: emptyList()
        val resolver = CollisionResolver(
            preferences = preferences,
            userConstraints = userConstraints
        )

        localStudyBlocks.forEach { studyBlock ->
            val collides = proposedBaseCalendar.any { it.overlaps(studyBlock) } ||
                           resolvedStudyBlocks.any { it.overlaps(studyBlock) }

            if (collides) {
                val currentCalendarState = proposedBaseCalendar + resolvedStudyBlocks
                val result = resolver.resolve(studyBlock, currentCalendarState)
                if (result is ResolutionResult.Success) {
                    val shifted = result.resolvedEvents.first()
                    val hasShifted = shifted.date != studyBlock.date ||
                            (shifted is TimeEvent && studyBlock is TimeEvent && (shifted.startTime != studyBlock.startTime || shifted.endTime != studyBlock.endTime))
                    if (hasShifted) {
                        val collidingEvent = proposedBaseCalendar.firstOrNull { it.overlaps(studyBlock) }
                                ?: resolvedStudyBlocks.firstOrNull { it.overlaps(studyBlock) }
                                ?: studyBlock
                        proposals.add(SyncProposal.StudyBlockShift(studyBlock, shifted, collidingEvent))
                        resolvedStudyBlocks.addAll(result.resolvedEvents)
                    } else {
                        resolvedStudyBlocks.add(studyBlock)
                    }
                } else {
                    val collidingEvent = proposedBaseCalendar.firstOrNull { it.overlaps(studyBlock) }
                            ?: resolvedStudyBlocks.firstOrNull { it.overlaps(studyBlock) }
                            ?: studyBlock
                    proposals.add(SyncProposal.StudyBlockShift(studyBlock, studyBlock, collidingEvent))
                    resolvedStudyBlocks.add(studyBlock)
                }
            } else {
                resolvedStudyBlocks.add(studyBlock)
            }
        }

        return SyncNegotiation(
            proposals = proposals,
            remoteEventsToSync = remoteEvents,
            deletedLocalIds = deletedLocalIds
        )
    }

    /**
     * Applies the sync negotiation results to the local and remote repositories.
     */
    suspend fun applySyncNegotiation(negotiation: SyncNegotiation, calendarId: String = "default") {
        val localEvents = localRepo.getAllEvents(calendarId)

        // 3. Remote is Gold Standard: Any local SYNCED event missing from Remote is deleted.
        negotiation.deletedLocalIds.forEach { id ->
            val local = localEvents.find { it.id == id }
            if (local != null && local.category == AcademicCategory.STUDY_BLOCK) {
                userPreferenceMemoryRepository?.logOverride(OverrideAction.DELETE, local)
            }
            localRepo.hardDeleteEvent(id, calendarId)
        }

        // 4. Remote is Gold Standard: Upsert ALL remote events to Local
        negotiation.remoteEventsToSync.forEach { remote ->
            val local = localEvents.find { it.id == remote.id }
            if (local != null && local.syncStatus == SyncStatus.SYNCED && local.updatedAt != remote.updatedAt) {
                if (local.category == AcademicCategory.STUDY_BLOCK && (local.date != remote.date || (local is TimeEvent && remote is TimeEvent && (local.startTime != remote.startTime || local.endTime != remote.endTime)))) {
                    userPreferenceMemoryRepository?.logOverride(OverrideAction.MOVE, local)
                }
                logger?.i(
                    "SyncConflict",
                    "WARNING: Event '${remote.title}' (ID: ${remote.id}) has conflicting updates. Remote (updatedAt: ${remote.updatedAt}) overrides Local (updatedAt: ${local.updatedAt})."
                )
            }
            localRepo.updateEvent(
                when (remote) {
                    is TimeEvent -> remote.copy(syncStatus = SyncStatus.SYNCED)
                    is DayEvent -> remote.copy(syncStatus = SyncStatus.SYNCED)
                },
                calendarId
            )
        }

        // 5. Save shifted study blocks (both locally and remote)
        negotiation.proposals.forEach { proposal ->
            if (proposal is SyncProposal.StudyBlockShift && proposal.proposedEvent != proposal.originalEvent) {
                val shifted = proposal.proposedEvent
                val runProfile = localRepo.getSettings()?.getString("run_profile", "local") ?: "local"
                if (runProfile != "test") {
                    try {
                        remoteRepo.saveEvent(shifted, calendarId)
                        localRepo.updateEvent(
                            when (shifted) {
                                is TimeEvent -> shifted.copy(syncStatus = SyncStatus.SYNCED)
                                is DayEvent -> shifted.copy(syncStatus = SyncStatus.SYNCED)
                            },
                            calendarId
                        )
                    } catch (e: Exception) {
                        localRepo.updateEvent(
                            when (shifted) {
                                is TimeEvent -> shifted.copy(syncStatus = SyncStatus.LOCAL_ONLY)
                                is DayEvent -> shifted.copy(syncStatus = SyncStatus.LOCAL_ONLY)
                            },
                            calendarId
                        )
                    }
                } else {
                    localRepo.updateEvent(shifted, calendarId)
                }
            }
        }
    }

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
    suspend fun getIncompleteEventsBefore(date: LocalDate, calendarId: String = "default"): List<Event> {
        return localRepo.getIncompleteEventsBefore(date, calendarId)
    }
}
