package com.borinquenterrier.cef

/**
 * Applies a [SyncNegotiation]'s results to the Local and Remote repositories:
 * removes events Remote no longer has, upserts Remote's events into Local (Remote is
 * the Gold Standard), and persists any Study Block shifts that were proposed.
 */
class SyncNegotiationApplier(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null
) {

    suspend fun apply(negotiation: SyncNegotiation, calendarId: String) {
        val localEvents = localRepo.getAllEvents(calendarId)

        // 3. Remote is Gold Standard: Any local SYNCED event missing from Remote is deleted.
        applyDeletedLocalEvents(negotiation.deletedLocalIds, localEvents, calendarId)

        // 4. Remote is Gold Standard: Upsert ALL remote events to Local
        applyRemoteEventsToLocal(negotiation.remoteEventsToSync, localEvents, calendarId)

        // 5. Save shifted study blocks (both locally and remote)
        applyShiftedStudyBlocks(negotiation.proposals, calendarId)
    }

    private suspend fun applyDeletedLocalEvents(
        deletedLocalIds: List<String>,
        localEvents: List<Event>,
        calendarId: String
    ) {
        deletedLocalIds.forEach { id ->
            val local = localEvents.find { it.id == id }
            if (local != null && local.category == AcademicCategory.STUDY_BLOCK) {
                userPreferenceMemoryRepository?.logOverride(OverrideAction.DELETE, local)
            }
            localRepo.hardDeleteEvent(id, calendarId)
        }
    }

    private suspend fun applyRemoteEventsToLocal(
        remoteEvents: List<Event>,
        localEvents: List<Event>,
        calendarId: String
    ) {
        remoteEvents.forEach { remote ->
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
            localRepo.updateEvent(remote.withSyncStatus(SyncStatus.SYNCED), calendarId)
        }
    }

    private suspend fun applyShiftedStudyBlocks(
        proposals: List<SyncProposal>,
        calendarId: String
    ) {
        proposals.forEach { proposal ->
            if (proposal is SyncProposal.StudyBlockShift && proposal.proposedEvent != proposal.originalEvent) {
                val shifted = proposal.proposedEvent
                if (isLiveSyncEnabled()) {
                    try {
                        remoteRepo.saveEvent(shifted, calendarId)
                        localRepo.updateEvent(shifted.withSyncStatus(SyncStatus.SYNCED), calendarId)
                    } catch (e: Exception) {
                        logger?.e("SyncNegotiationApplier", "Failed to push shifted study block to remote: ${shifted.title}", e)
                        localRepo.updateEvent(
                            shifted.withSyncStatus(SyncStatus.LOCAL_ONLY),
                            calendarId
                        )
                    }
                } else {
                    localRepo.updateEvent(shifted, calendarId)
                }
            }
        }
    }

    private suspend fun isLiveSyncEnabled(): Boolean =
        (localRepo.getSettings()?.getString("run_profile", "local") ?: "local") != "test"
}
