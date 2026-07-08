package com.borinquenterrier.cef

/**
 * Executes the calendar push logic for a batch of events extracted by [EventAgent].
 *
 * All state-flow mutations (isLoading, statusMessage, etc.) are delegated back via
 * callbacks so this class remains independently testable.
 */
internal class CalendarPusher(
    private val pushResolver: CalendarPushResolver,
    private val repository: CalendarAgent,
    private val logger: Logger?,
    private val onIsLoading: (Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onGeneratedEvents: (List<Event>) -> Unit,
    private val onUnresolvedConflicts: (List<ConflictResolver.UnresolvedConflict>) -> Unit,
    private val onErrorState: (AgentError) -> Unit
) {
    private val tag = "CalendarPusher"

    /**
     * Pushes [allEvents] via [pushResolver] and applies outcomes to state via the callbacks.
     * Returns the events that could not be pushed (conflicts), or an empty list on success.
     *
     * Does NOT filter by date. Per the Confabulation Gate mandate (AGENTS.md), students load
     * syllabi from any semester — past, current, or future — and every correctly-grounded event
     * must reach the calendar regardless of today's wall-clock date. A past-date filter here
     * previously discarded already-elapsed deliverables (e.g. an Issue Brief due earlier in the
     * same semester) before they were ever saved.
     */
    suspend fun push(allEvents: List<Event>, calendarId: String): List<Event> {
        if (allEvents.isEmpty()) return emptyList()

        onIsLoading(true)
        onStatus("Syncing ${allEvents.size} events to your calendar...")

        var conflicts: List<Event> = emptyList()
        try {
            val existing = repository.getEvents(calendarId)
            val outcome = pushResolver.resolveAndPush(allEvents, existing, calendarId)
            conflicts = outcome.conflicts

            if (outcome.unresolvableConflicts.isNotEmpty()) {
                onUnresolvedConflicts(outcome.unresolvableConflicts)
                logger?.d(tag, "Found ${outcome.unresolvableConflicts.size} unresolvable conflicts")
            }

            val localOnlyCount = outcome.localOnlyCount
            if (conflicts.isEmpty() && outcome.unresolvableConflicts.isEmpty()) {
                when {
                    outcome.successCount == 0 && localOnlyCount == 0 ->
                        onStatus("All events already synced — nothing new to push.")
                    localOnlyCount > 0 && outcome.successCount == 0 ->
                        onStatus("Saved $localOnlyCount events locally — could not reach Google Calendar.")
                    localOnlyCount > 0 ->
                        onStatus("Synced ${outcome.successCount} to Google Calendar, $localOnlyCount saved locally (offline).")
                    else ->
                        onStatus("Success! All ${outcome.successCount} events pushed to Google Calendar.")
                }
                onGeneratedEvents(emptyList())
            } else {
                val unresolvableCount = outcome.unresolvableConflicts.size
                val localNote = if (localOnlyCount > 0) ", $localOnlyCount saved locally" else ""
                onStatus("Synced ${outcome.successCount} events$localNote. $unresolvableCount require professor contact, ${conflicts.size} other conflicts.")
                onGeneratedEvents(conflicts)
            }
        } catch (e: CalendarNotFoundException) {
            logger?.e(tag, "Calendar not found during sync", e)
            val msg = e.message ?: "Calendar is no longer accessible. Please re-link your calendar."
            onStatus(msg)
            onErrorState(AgentError.GenericError(e.message ?: "Calendar sync failed"))
        } catch (e: Exception) {
            logger?.e(tag, "Error pushing to calendar", e)
            onStatus("Sync Error: ${e.message}")
        } finally {
            onIsLoading(false)
        }
        return conflicts
    }
}
