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

    // Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                "AI",
                "Hello! How can I help you today?"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Listeners for platform-specific UI (like native iOS)
    private var screenListener: ((AppScreen) -> Unit)? = null
    private var eventsListener: ((List<Event>) -> Unit)? = null

    // Expose delegated services
    val currentScreen: StateFlow<AppScreen> = navigationService.currentScreen
    val aiGeneratedEvents: StateFlow<List<Event>> = eventsService.aiGeneratedEvents
    val sourceItems: StateFlow<List<SourceItem>> = sourceManager.sourceItems
    val selectedSource: StateFlow<SourceItem?> = sourceManager.selectedSource

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

    fun addSource(source: SourceItem) {
        sourceManager.addSource(source)
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
        _chatMessages.value = _chatMessages.value + message
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
