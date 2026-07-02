package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight facade coordinating navigation, AI events, sources, and chat state.
 * Delegates to specialized services for each responsibility.
 */
class AppController(
    val container: DependencyContainer,
    // Injectable so tests can supply a deterministic dispatcher instead of Dispatchers.Main
    // (whose scheduling made the init retry-collector tests flaky).
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val sourceProcessingMutex = Mutex()

    private val navigationService = AppNavigationService()
    private val eventsService = AiEventsService()
    private val sourceManager = container.sourceManager

    // Chat State - wrapped for testability
    private val _chatMessagesWrapper: MutableStateFlowWrapper<List<ChatMessage>> =
        mutableStateFlowWrapper(
            listOf(
                ChatMessage(
                    "AI",
                    "Hello! How can I help you today?"
                )
            )
        )
    val chatMessages: StateFlowReader<List<ChatMessage>> = _chatMessagesWrapper

    // Listeners for platform-specific UI (like native iOS)
    private var screenListener: ((AppScreen) -> Unit)? = null
    private var eventsListener: ((List<Event>) -> Unit)? = null

    // Expose delegated services via StateFlowReader interfaces
    val currentScreen: StateFlowReader<AppScreen> = object : StateFlowReader<AppScreen> {
        override val value: AppScreen get() = navigationService.currentScreen.value
        override suspend fun collect(collector: suspend (AppScreen) -> Unit) {
            navigationService.currentScreen.collect(collector)
        }
        override fun asStateFlow() = navigationService.currentScreen
    }

    val aiGeneratedEvents: StateFlowReader<List<Event>> = object : StateFlowReader<List<Event>> {
        override val value: List<Event> get() = eventsService.aiGeneratedEvents.value
        override suspend fun collect(collector: suspend (List<Event>) -> Unit) {
            eventsService.aiGeneratedEvents.collect(collector)
        }
        override fun asStateFlow() = eventsService.aiGeneratedEvents
    }

    val sourceItems: StateFlowReader<List<SourceItem>> = sourceManager.sourceItems
    val selectedSource: StateFlowReader<SourceItem?> = sourceManager.selectedSource

    init {
        scope.launch {
            currentScreen.collect { screen ->
                screenListener?.invoke(screen)
            }
        }
        scope.launch {
            aiGeneratedEvents.collect { events ->
                eventsListener?.invoke(events)
            }
        }
        // Flush LOCAL_ONLY events as soon as Google is confirmed linked, so events that
        // failed to reach the remote calendar in a previous session are retried automatically.
        scope.launch {
            container.tokenRepository.isLinked.filter { it }.take(1).collect {
                container.calendarAgent.retryLocalOnly()
            }
        }
        // Restore persisted sources into the in-memory list at startup. Sources are saved to the
        // DB on add but were never reloaded, so the sources panel and chat (which rank in-memory
        // fragments) came up empty after every restart. Loading is a pure DB read — it does NOT
        // re-trigger processing, which stays once-per-source via the analysis cache.
        loadSources()
        // Startup integrity check: surface any out-of-term drift for review (read-only; the safe
        // drift is auto-corrected by self-heal on the next sync). Never fatal.
        scope.launch {
            runCatching { container.calendarAgent.checkHealth() }
        }
    }

    fun loadSources() {
        scope.launch {
            sourceManager.loadSources()
        }
    }

    fun navigateTo(screen: AppScreen) {
        navigationService.navigateTo(screen)
    }

    fun addEvents(events: List<Event>) {
        eventsService.addEvents(events)
    }

    fun clearEvents() {
        eventsService.clearEvents()
    }

    fun resetForDemo() {
        launchInScope {
            container.calendarAgent.resetCalendar()
            container.eventAgent.clear()
            eventsService.clearEvents()
        }
    }

    /** Read-only calendar health check (duplicates, out-of-term, stale timestamps). */
    suspend fun checkCalendar(): ReconciliationReport = container.calendarAgent.reconcile()

    /** Applies the fixes in [report] (deletes drift, stamps stale timestamps) local + remote. */
    fun repairCalendar(report: ReconciliationReport) {
        launchInScope { container.calendarAgent.applyReconciliation(report) }
    }

    fun addSource(source: SourceItem, forceRefresh: Boolean = false) {
        sourceManager.registerSource(source)
        processSourceAutoPush(source)
    }

    fun reanalyzeSource(source: SourceItem) {
        sourceManager.registerSource(source)
        processSourceAutoPush(source)
    }

    /**
     * Runs the full auto-push pipeline for [source] (context → extract → push → decompose →
     * study plan → pause-on-doubt → push) instead of only staging events for a manual push.
     * Serialized so concurrent adds (multi-file picker) don't interleave push/dedup on the
     * shared generated-event state.
     */
    private fun processSourceAutoPush(source: SourceItem) {
        launchInScope {
            sourceProcessingMutex.withLock {
                try {
                    container.harnessSourceProcessor.processSource(source)
                } catch (e: Exception) {
                    container.logger.e("AppController", "Auto-push processing failed: ${source.title}", e)
                }
            }
        }
    }

    fun launchInScope(block: suspend CoroutineScope.() -> Unit) {
        scope.launch { block() }
    }

    fun deleteSource(source: SourceItem) {
        sourceManager.deleteSource(source)
    }

    fun selectSource(source: SourceItem?) {
        sourceManager.selectSource(source)
    }

    fun addChatMessage(message: ChatMessage) {
        _chatMessagesWrapper.setValue(_chatMessagesWrapper.value + message)
    }

    fun setScreenListener(listener: (AppScreen) -> Unit) {
        this.screenListener = listener
        listener(currentScreen.value)
    }

    fun setEventsListener(listener: (List<Event>) -> Unit) {
        this.eventsListener = listener
        listener(aiGeneratedEvents.value)
    }
}

sealed class AppScreen {
    object Home : AppScreen()
    object Calendar : AppScreen()
    object Settings : AppScreen()
    object Routine : AppScreen()
}
