package com.borinquenterrier.cef

/**
 * Compares Local and Remote calendar state and compiles a [SyncNegotiation] proposal,
 * including pushing pending local changes to Remote and resolving Study Block collisions
 * against the proposed merged calendar.
 */
class SyncNegotiator(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null,
    private val preferencesRepository: PreferencesRepository? = null,
    private val logger: Logger? = null
) {
    private val tag = "SyncNegotiator"
    private val studyBlockShiftResolver =
        StudyBlockShiftResolver(userPreferenceMemoryRepository, preferencesRepository)

    suspend fun buildNegotiation(calendarId: String): SyncNegotiation {
        // First push local additions and deletions to Remote so Remote is updated
        pushLocalChanges(calendarId)

        val localEvents = localRepo.getAllEvents(calendarId)
        val remoteEvents = try {
            remoteRepo.getAllEvents(calendarId)
        } catch (e: Exception) {
            logger?.e(tag, "Failed to fetch remote events during negotiation", e)
            // If offline, return empty negotiation
            return SyncNegotiation(emptyList(), emptyList(), emptyList())
        }

        val remoteUpdates = mutableListOf<Event>()
        val directConflicts = mutableListOf<SyncProposal.DirectConflict>()

        findRemoteUpdatesAndConflicts(localEvents, remoteEvents, remoteUpdates, directConflicts)

        val deletedLocalIds = findDeletedLocalIds(localEvents, remoteEvents)

        val proposedBaseCalendar =
            buildProposedBaseCalendar(localEvents, remoteUpdates, deletedLocalIds)

        val shiftProposals =
            studyBlockShiftResolver.resolveShifts(localEvents, proposedBaseCalendar)

        return SyncNegotiation(
            proposals = directConflicts + shiftProposals,
            remoteEventsToSync = remoteEvents,
            deletedLocalIds = deletedLocalIds
        )
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
                    logger?.e(tag, "Failed to push local deletion for event $id", e)
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
                logger?.e(tag, "Failed to push local creation for event ${local.id}", e)
                // Network failure or overlap on remote, keep as LOCAL_ONLY
            }
        }
    }

    private fun findRemoteUpdatesAndConflicts(
        localEvents: List<Event>,
        remoteEvents: List<Event>,
        remoteUpdates: MutableList<Event>,
        directConflicts: MutableList<SyncProposal.DirectConflict>
    ) {
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
    }

    private fun findDeletedLocalIds(
        localEvents: List<Event>,
        remoteEvents: List<Event>
    ): List<String> {
        return localEvents
            .filter { it.syncStatus == SyncStatus.SYNCED }
            .filter { local -> remoteEvents.none { it.id == local.id } }
            .mapNotNull { it.id }
    }

    private fun buildProposedBaseCalendar(
        localEvents: List<Event>,
        remoteUpdates: List<Event>,
        deletedLocalIds: List<String>
    ): List<Event> {
        val nonStudyBlocks =
            localEvents.filter { it.category != AcademicCategory.STUDY_BLOCK && it.id !in deletedLocalIds }
        val updatedNonStudyBlocks = nonStudyBlocks.map { local ->
            remoteUpdates.find { it.id == local.id } ?: local
        }
        val newRemoteNonStudyBlocks = remoteUpdates.filter { remote ->
            remote.category != AcademicCategory.STUDY_BLOCK && localEvents.none { it.id == remote.id }
        }
        return updatedNonStudyBlocks + newRemoteNonStudyBlocks
    }
}
