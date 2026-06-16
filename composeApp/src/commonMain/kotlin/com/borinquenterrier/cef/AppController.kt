package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight facade coordinating navigation, AI events, sources, and chat state.
 * Delegates to specialized services for each responsibility.
 */
class AppController(val container: DependencyContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        scope.launch {
            sourceManager.loadSources()
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

    fun addSource(source: SourceItem, forceRefresh: Boolean = false) {
        sourceManager.addSource(source, forceRefresh)
    }

    fun reanalyzeSource(source: SourceItem) {
        sourceManager.reanalyzeSource(source)
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
