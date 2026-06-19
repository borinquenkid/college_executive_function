package com.borinquenterrier.cef

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

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
    private val clock: Clock = Clock.System,
    private val onIsLoading: (Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onGeneratedEvents: (List<Event>) -> Unit,
    private val onUnresolvedConflicts: (List<ConflictResolver.UnresolvedConflict>) -> Unit,
    private val onErrorState: (AgentError) -> Unit
) {
    private val tag = "CalendarPusher"

    /**
     * Filters [allEvents] to future-only, pushes them via [pushResolver], and applies
     * outcomes to state via the callbacks. Returns the events that could not be pushed
     * (conflicts), or an empty list on success or when all events are in the past.
     */
    suspend fun push(allEvents: List<Event>, calendarId: String): List<Event> {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val events = allEvents.filter { it.date >= today }
        val skippedCount = allEvents.size - events.size

        if (allEvents.isEmpty()) return emptyList()
        if (events.isEmpty()) {
            onStatus("No future events to sync ($skippedCount past events skipped).")
            onGeneratedEvents(emptyList())
            return emptyList()
        }

        onIsLoading(true)
        val skippedNote = if (skippedCount > 0) " ($skippedCount past events skipped)" else ""
        onStatus("Syncing ${events.size} events to your calendar...$skippedNote")

        var conflicts: List<Event> = emptyList()
        try {
            val existing = repository.getEvents(calendarId)
            val outcome = pushResolver.resolveAndPush(events, existing, calendarId)
            conflicts = outcome.conflicts

            if (outcome.unresolvableConflicts.isNotEmpty()) {
                onUnresolvedConflicts(outcome.unresolvableConflicts)
                logger?.d(tag, "Found ${outcome.unresolvableConflicts.size} unresolvable conflicts")
            }

            if (conflicts.isEmpty() && outcome.unresolvableConflicts.isEmpty()) {
                onStatus("Success! All ${outcome.successCount} events pushed.$skippedNote")
                onGeneratedEvents(emptyList())
            } else {
                val unresolvableCount = outcome.unresolvableConflicts.size
                onStatus("Synced ${outcome.successCount} events. $unresolvableCount require professor contact, ${conflicts.size} other conflicts.$skippedNote")
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
