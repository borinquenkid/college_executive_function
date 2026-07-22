package com.borinquenterrier.cef

import kotlin.time.Clock

/**
 * Compares Local and Remote calendar state and compiles a [SyncNegotiation] proposal,
 * including pushing pending local changes to Remote and resolving Study Block collisions
 * against the proposed merged calendar.
 */
class SyncNegotiator(
    private val localRepo: StudentCalendarRepository,
    private val remoteRepo: RemoteCalendarRepository,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp,
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp,
    private val logger: Logger? = null,
    private val clock: Clock = Clock.System,
    private val deletionGracePeriodMs: Long = DELETE_GRACE_PERIOD_MS
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
        } catch (e: CalendarNotFoundException) {
            throw e
        } catch (e: Exception) {
            logger?.e(tag, "Failed to fetch remote events for calendar $calendarId, returning empty negotiation", e)
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
                    // Network failure, keep as DELETED_LOCALLY to try again later
                    logger?.e(tag, "Remote delete failed, keeping '${local.title}' as DELETED_LOCALLY", e)
                }
            }
        }

        // 2. Handle Local Creations: PUSH new events to Remote, then mark as SYNCED
        localRepo.getEventsBySyncStatus(SyncStatus.LOCAL_ONLY, calendarId).forEach { local ->
            try {
                remoteRepo.saveEvent(local, calendarId)
                localRepo.updateEvent(local.withSyncStatus(SyncStatus.SYNCED), calendarId)
            } catch (e: Exception) {
                // Network failure or overlap on remote, keep as LOCAL_ONLY to retry next sync
                logger?.e(tag, "Remote push failed, keeping '${local.title}' as LOCAL_ONLY", e)
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
                // Only sync remote→local when the CONTENT actually differs. A timestamp-only
                // difference (identical fields) is the same event with a newer server clock — the
                // remote clock always advances past our generation stamp on each push, so overriding
                // on it alone caused an endless "remote overrides local" churn every sync.
                val hasFieldDiff = local.title != remote.title || local.date != remote.date ||
                        (local is TimeEvent && remote is TimeEvent && (local.startTime != remote.startTime || local.endTime != remote.endTime))
                if (hasFieldDiff) {
                    remoteUpdates.add(remote)
                    directConflicts.add(SyncProposal.DirectConflict(local, remote))
                }
            }
        }
    }

    /**
     * A locally-SYNCED event absent from the remote list is only treated as remote-deleted once
     * it's older than [deletionGracePeriodMs]. Google Calendar's list endpoint isn't guaranteed to
     * reflect a just-created event on the very next call — without this grace period, a sync that
     * races a just-pushed event (e.g. the background harness poll running right after ingesting a
     * new source) misreads that lag as a remote deletion and hard-deletes it locally.
     */
    private fun findDeletedLocalIds(
        localEvents: List<Event>,
        remoteEvents: List<Event>
    ): List<String> {
        val now = clock.now().toEpochMilliseconds()
        return localEvents
            .filter { it.syncStatus == SyncStatus.SYNCED }
            .filter { now - it.updatedAt >= deletionGracePeriodMs }
            .filter { local -> remoteEvents.none { it.id == local.id } }
            .mapNotNull { it.id }
    }

    companion object {
        /** 5 minutes — comfortably longer than Google Calendar's observed read-after-write lag. */
        const val DELETE_GRACE_PERIOD_MS = 5 * 60 * 1000L
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
