package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Typed error states surfaced from [EventAgent] to the UI.
 *
 * - [QuotaExhausted]: Free-tier daily limit reached. Retrying will not help until quota resets.
 * - [GenericError]: Any other transient or structural failure.
 */
sealed class AgentError {
    data object QuotaExhausted : AgentError()
    data class GenericError(val message: String) : AgentError()
}

/**
 * Encapsulates the business logic for the Studio panel, 
 * separating AI processing and event management from the UI.
 */
class EventAgent(
    private val aiService: AIService,
    private val repository: CalendarAgent,
    private val database: AppDatabase? = null,
    private val normalizationService: NormalizationService = NormalizationService(),
    private val preferencesRepository: PreferencesRepository? = null,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null
) {
    private val tag = "EventAgent"

    private val syllabusAuditor = SyllabusAuditor(aiService, logger)
    private val pushResolver = CalendarPushResolver(
        repository,
        preferencesRepository,
        userPreferenceMemoryRepository,
        logger
    )
    private val generationService = EventGenerationService(
        aiService,
        normalizationService,
        syllabusAuditor,
        preferencesRepository,
        userPreferenceMemoryRepository
    )
    private val decompositionService = TaskDecompositionService(aiService, repository)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("Select a source and an action.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastGeneratedEvents = MutableStateFlow<List<Event>>(emptyList())
    val lastGeneratedEvents: StateFlow<List<Event>> = _lastGeneratedEvents.asStateFlow()

    private val _decomposedTasks = MutableStateFlow<List<DecomposedTask>>(emptyList())
    val decomposedTasks: StateFlow<List<DecomposedTask>> = _decomposedTasks.asStateFlow()

    private val _decompositionTarget = MutableStateFlow<Event?>(null)
    val decompositionTarget: StateFlow<Event?> = _decompositionTarget.asStateFlow()

    private val _errorState = MutableStateFlow<AgentError?>(null)

    /** Non-null when an error requiring user attention has occurred. Call [clearError] to dismiss. */
    val errorState: StateFlow<AgentError?> = _errorState.asStateFlow()

    private val _unresolvedConflicts =
        MutableStateFlow<List<ConflictResolver.UnresolvedConflict>>(emptyList())

    /** Conflicts requiring professor approval (quiz, test, exam conflicts). */
    val unresolvedConflicts: StateFlow<List<ConflictResolver.UnresolvedConflict>> =
        _unresolvedConflicts.asStateFlow()

    fun clearError() {
        _errorState.value = null
    }

    fun clearUnresolvedConflicts() {
        _unresolvedConflicts.value = emptyList()
    }

    fun updateStatus(message: String) {
        _statusMessage.value = message
    }

    /** Returns true if the exception message signals a daily quota exhaustion. */
    private fun Exception.isQuotaError() =
        message?.startsWith("QuotaExhausted") == true

    /**
     * Runs [block] under the standard loading/error-reporting envelope shared by every
     * agent action: toggles [_isLoading], logs and reports any [Exception] via
     * [_statusMessage] (and optionally [_errorState] for quota exhaustion), and always
     * resets [_isLoading] when done.
     */
    private suspend fun runAgentAction(
        logContext: String,
        handleQuotaErrors: Boolean = false,
        block: suspend () -> Unit
    ) {
        _isLoading.value = true
        try {
            block()
        } catch (e: Exception) {
            logger?.e(tag, logContext, e)
            if (handleQuotaErrors && e.isQuotaError()) {
                _errorState.value = AgentError.QuotaExhausted
                _statusMessage.value = "Daily AI quota reached."
            } else {
                _statusMessage.value = "Error: ${e.message}"
            }
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Updates [event]'s [CompletionStatus], persists it, refreshes the incomplete-events
     * list, and optionally triggers a background sync (errors from which are ignored).
     */
    private suspend fun updateCompletionStatus(
        event: Event,
        status: CompletionStatus,
        statusMessage: String,
        triggerSync: Boolean = false
    ) {
        val updated = event.withCompletionStatus(status)
        repository.updateEvent(updated, "default")
        _statusMessage.value = statusMessage
        loadIncompleteEvents()
        if (triggerSync) {
            try {
                repository.synchronize("default")
            } catch (e: Exception) {
                // Ignore sync errors
            }
        }
    }

    /**
     * Extracts standard deliverables from a source using AI, processing the full context at once.
     */
    suspend fun extractDeliverables(source: SourceItem) {
        runAgentAction("Error extracting deliverables", handleQuotaErrors = true) {
            _statusMessage.value = "Analyzing ${source.title}..."
            val processed = generationService.extractDeliverables(source)
            _lastGeneratedEvents.value = processed
            _statusMessage.value = "${processed.size} deadlines and exams found from entire source."
        }
    }

    /**
     * Generates a proactive study plan, using the full document context and existing calendar events.
     */
    suspend fun generateStudyPlan(source: SourceItem) {
        runAgentAction("Error generating study plan", handleQuotaErrors = true) {
            _statusMessage.value = "Planning study time from full context..."
            val existingEvents = repository.getEvents("default")
            val processed = generationService.generateStudyPlan(source, existingEvents)
            _lastGeneratedEvents.value = processed
            _statusMessage.value = "${processed.size} events planned for study time."
        }
    }

    /**
     * Pushes the last generated batch of events to the calendar, checking for conflicts.
     * Returns a list of events that COULD NOT be pushed due to overlaps.
     */
    suspend fun pushToCalendar(calendarId: String = "default"): List<Event> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val allEvents = _lastGeneratedEvents.value
        val events = allEvents.filter { it.date >= today }
        val skippedCount = allEvents.size - events.size
        if (allEvents.isEmpty()) return emptyList()
        if (events.isEmpty()) {
            _statusMessage.value = "No future events to sync ($skippedCount past events skipped)."
            _lastGeneratedEvents.value = emptyList()
            return emptyList()
        }

        _isLoading.value = true
        val skippedNote = if (skippedCount > 0) " ($skippedCount past events skipped)" else ""
        _statusMessage.value = "Syncing ${events.size} events to your calendar...$skippedNote"

        var conflicts: List<Event> = emptyList()

        try {
            val existing = repository.getEvents(calendarId)
            val outcome = pushResolver.resolveAndPush(events, existing, calendarId)
            conflicts = outcome.conflicts

            // Capture unresolved conflicts (those requiring professor approval)
            if (outcome.unresolvableConflicts.isNotEmpty()) {
                _unresolvedConflicts.value = outcome.unresolvableConflicts
                logger?.d(
                    tag,
                    "Found ${outcome.unresolvableConflicts.size} unresolvable conflicts requiring professor approval"
                )
            }

            if (conflicts.isEmpty() && outcome.unresolvableConflicts.isEmpty()) {
                _statusMessage.value = "Success! All ${outcome.successCount} events pushed.$skippedNote"
                _lastGeneratedEvents.value = emptyList()
            } else {
                val unresolvableCount = outcome.unresolvableConflicts.size
                _statusMessage.value =
                    "Synced ${outcome.successCount} events. $unresolvableCount require professor contact, ${conflicts.size} other conflicts.$skippedNote"
                _lastGeneratedEvents.value = conflicts
            }
        } catch (e: CalendarNotFoundException) {
            logger?.e(tag, "Calendar not found during sync", e)
            _statusMessage.value =
                e.message ?: "Calendar is no longer accessible. Please re-link your calendar."
            _errorState.value = AgentError.GenericError(e.message ?: "Calendar sync failed")
        } catch (e: Exception) {
            logger?.e(tag, "Error pushing to calendar", e)
            _statusMessage.value = "Sync Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
        return conflicts
    }

    fun clear() {
        _lastGeneratedEvents.value = emptyList()
        _statusMessage.value = "Select a source and an action."
    }

    suspend fun decomposeTask(event: Event) {
        runAgentAction("Error decomposing task", handleQuotaErrors = true) {
            _decompositionTarget.value = event
            _decomposedTasks.value = emptyList()
            _statusMessage.value = "Breaking down '${event.title}'..."

            val tasks = decompositionService.decompose(event)
            _decomposedTasks.value = tasks
            _statusMessage.value = "${tasks.size} steps created."
        }
    }

    suspend fun acceptDecomposition(calendarId: String = "default") {
        val tasks = _decomposedTasks.value
        val target = _decompositionTarget.value ?: return
        if (tasks.isEmpty()) return

        runAgentAction("Error accepting decomposition") {
            _statusMessage.value = "Adding ${tasks.size} steps to calendar..."
            val count = decompositionService.applyDecomposition(target, tasks, calendarId)
            _statusMessage.value = "$count steps added to calendar."
            clearDecomposition()
        }
    }

    fun clearDecomposition() {
        _decomposedTasks.value = emptyList()
        _decompositionTarget.value = null
    }

    private val _incompleteEvents = MutableStateFlow<List<Event>>(emptyList())
    val incompleteEvents: StateFlow<List<Event>> = _incompleteEvents.asStateFlow()

    suspend fun loadIncompleteEvents() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        try {
            val list = repository.getIncompleteEventsBefore(today, "default")
            _incompleteEvents.value = list
        } catch (e: Exception) {
            logger?.e(tag, "Failed to load incomplete events", e)
        }
    }

    suspend fun markEventCompleted(event: Event) {
        runAgentAction("Failed to mark event completed") {
            updateCompletionStatus(
                event,
                CompletionStatus.COMPLETED,
                "Marked '${event.title}' as completed.",
                triggerSync = true
            )
        }
    }

    suspend fun skipEvent(event: Event) {
        runAgentAction("Failed to skip event") {
            updateCompletionStatus(event, CompletionStatus.SKIPPED, "Skipped '${event.title}'.")
        }
    }

    suspend fun rescheduleEvent(event: Event) {
        runAgentAction("Failed to reschedule event") {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val updated = when (event) {
                is TimeEvent -> event.copy(date = today)
                is DayEvent -> event.copy(date = today)
            }
            val existing = repository.getEvents("default").toMutableList()
            existing.removeAll { it.id == event.id }

            val rescheduled = pushResolver.resolveAndReschedule(updated, existing, "default")
            _statusMessage.value = if (rescheduled) {
                "Rescheduled '${event.title}' successfully."
            } else {
                "Cannot reschedule: conflict detected."
            }
            loadIncompleteEvents()
        }
    }
}
