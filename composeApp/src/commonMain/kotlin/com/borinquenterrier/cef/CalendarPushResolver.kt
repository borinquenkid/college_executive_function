package com.borinquenterrier.cef

/** Outcome of pushing a batch of events: how many were saved, which ones still conflict, and which require professor approval. */
data class PushOutcome(
    val successCount: Int,
    val conflicts: List<Event>,
    val unresolvableConflicts: List<ConflictResolver.UnresolvedConflict> = emptyList(),
    val localOnlyCount: Int = 0
)

/**
 * Resolves a batch of candidate [Event]s against the existing calendar (and against
 * each other within the batch) using [CollisionResolver], then persists the resolved
 * events — routing any [OverlapException] encountered while saving into the conflict list.
 */
class CalendarPushResolver(
    private val repository: CalendarAgent,
    private val preferencesRepository: PreferencesRepository? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null,
    private val logger: Logger? = null
) {
    private val tag = "CalendarPushResolver"
    private val conflictResolver = ConflictResolver(logger)

    suspend fun resolveAndPush(
        events: List<Event>,
        existing: List<Event>,
        calendarId: String
    ): PushOutcome {
        // Phase 1: Use ConflictResolver for intelligent conflict handling
        val (mergedEvents, unresolvableConflicts) = conflictResolver.resolveConflicts(
            existing,
            events
        )

        // Phase 2: Separate merged events from existing (what's truly new).
        // An event is "already handled" only if it is confirmed SYNCED to remote.
        // LOCAL_ONLY events (saved locally when a previous remote push failed) must be
        // re-attempted here so they actually reach Google Calendar.
        val synced = existing.filter { it.syncStatus == SyncStatus.SYNCED }
        val newEvents = mergedEvents.filter { merged ->
            synced.none { it.id == merged.id } &&
            synced.none { it.title == merged.title && it.date == merged.date }
        }

        val conflicts = mutableListOf<Event>()
        val resolvedList = mutableListOf<Event>()

        // Phase 3: Use CollisionResolver as fallback for any remaining collisions
        val currentCalendarState = existing.toMutableList()
        val resolver = buildResolver()

        for (event in newEvents) {
            when (val result = resolver.resolve(event, currentCalendarState)) {
                is ResolutionResult.Success -> {
                    for (resolved in result.resolvedEvents) {
                        resolved.id?.let { id ->
                            currentCalendarState.removeAll { it.id == id }
                        }
                        currentCalendarState.add(resolved)
                    }
                    resolvedList.addAll(result.resolvedEvents)
                }

                is ResolutionResult.Conflict -> {
                    conflicts.add(event)
                }
            }
        }

        val localOnlyExisting = existing.filter { it.syncStatus == SyncStatus.LOCAL_ONLY }
        val (successCount, localOnlyCount) = persistResolvedEvents(resolvedList, calendarId, conflicts, localOnlyExisting)
        return PushOutcome(successCount, conflicts, unresolvableConflicts, localOnlyCount)
    }

    /**
     * Resolves [updated] (a single event being moved) against [existing], persisting the
     * result if successful. Returns true if the event was rescheduled, false on conflict.
     */
    suspend fun resolveAndReschedule(
        updated: Event,
        existing: List<Event>,
        calendarId: String
    ): Boolean {
        return when (val result = buildResolver().resolve(updated, existing)) {
            is ResolutionResult.Success -> {
                for (resolved in result.resolvedEvents) {
                    repository.updateEvent(resolved, calendarId)
                }
                true
            }

            is ResolutionResult.Conflict -> false
        }
    }

    private suspend fun buildResolver(): CollisionResolver {
        val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
        val userConstraints = userPreferenceMemoryRepository?.getDerivedConstraints() ?: emptyList()
        return CollisionResolver(preferences = preferences, userConstraints = userConstraints)
    }

    private suspend fun resolveConflicts(
        events: List<Event>,
        existing: List<Event>,
        conflicts: MutableList<Event>
    ): List<Event> {
        val currentCalendarState = existing.toMutableList()
        val resolvedList = mutableListOf<Event>()
        val resolver = buildResolver()

        for (event in events) {
            when (val result = resolver.resolve(event, currentCalendarState)) {
                is ResolutionResult.Success -> {
                    // Update currentCalendarState so subsequent events in this batch resolve against it
                    for (resolved in result.resolvedEvents) {
                        resolved.id?.let { id ->
                            currentCalendarState.removeAll { it.id == id }
                        }
                        currentCalendarState.add(resolved)
                    }
                    resolvedList.addAll(result.resolvedEvents)
                }

                is ResolutionResult.Conflict -> {
                    conflicts.add(event)
                }
            }
        }
        return resolvedList
    }

    private suspend fun persistResolvedEvents(
        resolvedList: List<Event>,
        calendarId: String,
        conflicts: MutableList<Event>,
        localOnlyExisting: List<Event> = emptyList()
    ): Pair<Int, Int> {
        var successCount = 0
        var localOnlyCount = 0
        for (resolved in resolvedList) {
            try {
                // Purge stale LOCAL_ONLY rows with the same title+date before re-saving.
                // LOCAL_ONLY events were never on remote, so hard-deleting them is safe.
                localOnlyExisting
                    .filter { it.title == resolved.title && it.date == resolved.date }
                    .mapNotNull { it.id }
                    .forEach { staleId -> repository.hardDeleteLocalOnly(staleId, calendarId) }
                repository.saveEvent(resolved, calendarId)
                successCount++
            } catch (e: OverlapException) {
                logger?.d(tag, "Conflict detected for: ${resolved.title}")
                conflicts.add(resolved)
            } catch (e: RemoteSyncFailedException) {
                logger?.d(tag, "Remote push failed, saved locally: ${resolved.title}")
                localOnlyCount++
            }
        }
        return Pair(successCount, localOnlyCount)
    }
}
