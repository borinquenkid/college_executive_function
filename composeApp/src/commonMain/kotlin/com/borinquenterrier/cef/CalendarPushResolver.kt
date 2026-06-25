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
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp,
    private val logger: Logger? = null
) {
    private val conflictResolver = ConflictResolver(logger)
    private val writer = ResolvedEventWriter(repository, logger)

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
        // Title comparison uses submissionCanonical() so AI casing/prefix variations
        // ("homework 1 due" vs "Homework 1 Due", "Submit HW 1" vs "HW 1") on re-extraction
        // don't bypass deduplication.
        val synced = existing.filter { it.syncStatus == SyncStatus.SYNCED }
        val newEvents = mergedEvents.filter { merged ->
            val mergedCanon = EventDeduplicator.submissionCanonical(merged.title)
            synced.none { it.id == merged.id } &&
            synced.none { EventDeduplicator.submissionCanonical(it.title) == mergedCanon && it.date == merged.date }
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
        val (successCount, localOnlyCount) = writer.persist(resolvedList, calendarId, conflicts, localOnlyExisting)
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
        val preferences = preferencesRepository.getPreferences()
        val userConstraints = userPreferenceMemoryRepository.getDerivedConstraints()
        return CollisionResolver(preferences = preferences, userConstraints = userConstraints)
    }
}
