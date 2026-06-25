package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
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
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp,
    private val logger: Logger? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp,
    private val clock: Clock = Clock.System
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
    private val autoDecomposer = AutoDecomposer(repository, decompositionService, clock)
    private val deliverableExtractor = DeliverableExtractor(
        generationService = generationService,
        onProgress = { _statusMessage.value = it }
    )
    private val decompositionAcceptor = DecompositionAcceptor(decompositionService)
    private val eventRescheduler = EventRescheduler(pushResolver, repository, clock)
    private val calendarPusher = CalendarPusher(
        pushResolver = pushResolver,
        repository = repository,
        logger = logger,
        clock = clock,
        onIsLoading = { _isLoading.value = it },
        onStatus = { _statusMessage.value = it },
        onGeneratedEvents = { _lastGeneratedEvents.value = it },
        onUnresolvedConflicts = { _unresolvedConflicts.value = it },
        onErrorState = { _errorState.value = it }
    )

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

    private val _persistedWarnings = MutableStateFlow<List<String>>(emptyList())

    /** Unique AI warnings from all previously pushed events, surviving app restarts. */
    val persistedWarnings: StateFlow<List<String>> = _persistedWarnings.asStateFlow()

    private val _extractionWarning = MutableStateFlow<String?>(null)

    /**
     * Set when the last extraction run completed with zero events. Cleared on the next
     * successful extraction so it does not linger after the user loads a better document.
     */
    val extractionWarning: StateFlow<String?> = _extractionWarning.asStateFlow()

    /** Number of API requests currently waiting in the shared Gemini queue. */
    val pendingRequestCount: StateFlow<Int> = GeminiRequestQueue.shared().pendingCount

    /**
     * Rough lower-bound estimate of remaining processing time in seconds.
     * Assumes 3 s average response time per slot plus the 6 s inter-request cooldown.
     * Returns 0 when the queue is empty.
     */
    fun estimatedRemainingSeconds(): Int = GeminiRequestQueue.shared().estimatedRemainingSeconds()

    suspend fun loadPersistedWarnings() {
        try {
            _persistedWarnings.value = repository.getEvents("default")
                .mapNotNull { it.warning }
                .distinct()
        } catch (e: Exception) {
            logger?.e(tag, "Failed to load persisted warnings", e)
        }
    }

    fun clearError() {
        _errorState.value = null
    }

    fun reportError(error: AgentError) {
        _errorState.value = error
    }

    fun setGeneratedEvents(events: List<Event>) {
        _lastGeneratedEvents.value = events
        _statusMessage.value = "${events.size} events ready to sync."
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
            _extractionWarning.value = null
            val result = deliverableExtractor.run(source, "default")
            _lastGeneratedEvents.value = result.events
            _extractionWarning.value = result.warning
            _statusMessage.value = result.statusMessage
        }
    }

    /**
     * Automatically decomposes all unplanned DEADLINE/FINALS events in the calendar into
     * STUDY_BLOCK steps. Runs after the first [pushToCalendar] in the pipeline so the events
     * are already persisted and can be updated with a [studyPlanStart] marker.
     *
     * Skips events that already have a [studyPlanStart] (already decomposed).
     */
    suspend fun autoDecomposeDeliverables(calendarId: String = "default") {
        runAgentAction("Error auto-decomposing deliverables", handleQuotaErrors = true) {
            _statusMessage.value = "Breaking down deliverables into study steps..."
            val result = autoDecomposer.run(Unit, calendarId)
            _statusMessage.value = result.statusMessage
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
    suspend fun pushToCalendar(calendarId: String = "default"): List<Event> =
        AppTracer.current.span(
            "events.push_to_calendar",
            mapOf("calendar.id" to calendarId, "events.count" to _lastGeneratedEvents.value.size.toString())
        ) {
            val conflicts = calendarPusher.push(_lastGeneratedEvents.value, calendarId)
            loadPersistedWarnings()
            setAttribute("events.conflicts_count", conflicts.size.toLong())
            conflicts
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
        runAgentAction("Error accepting decomposition") {
            val count = decompositionAcceptor.run(
                AcceptInput(_decomposedTasks.value, _decompositionTarget.value),
                calendarId
            )
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
        val today = clock.todayIn(TimeZone.currentSystemDefault())
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
            _statusMessage.value = eventRescheduler.run(event, "default")
            loadIncompleteEvents()
        }
    }
}
