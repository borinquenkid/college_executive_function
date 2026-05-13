package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A centralized controller that manages the overall application state and navigation.
 * It acts as the bridge between the core logic (DependencyContainer) and the various UIs.
 */
class AppController(val container: DependencyContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Navigation State
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Data State
    private val _aiGeneratedEvents = MutableStateFlow<List<Event>>(emptyList())
    val aiGeneratedEvents: StateFlow<List<Event>> = _aiGeneratedEvents.asStateFlow()

    // Source State
    private val _sourceItems = MutableStateFlow<List<SourceItem>>(emptyList())
    val sourceItems: StateFlow<List<SourceItem>> = _sourceItems.asStateFlow()

    private val _selectedSource = MutableStateFlow<SourceItem?>(null)
    val selectedSource: StateFlow<SourceItem?> = _selectedSource.asStateFlow()

    // Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(ChatMessage("AI", "Hello! How can I help you today?")))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Listeners for platform-specific UI (like native iOS)
    private var screenListener: ((AppScreen) -> Unit)? = null
    private var eventsListener: ((List<Event>) -> Unit)? = null

    init {
        // Collect flows and notify listeners if any
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
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun addEvents(events: List<Event>) {
        _aiGeneratedEvents.value = _aiGeneratedEvents.value + events
    }

    fun clearEvents() {
        _aiGeneratedEvents.value = emptyList()
    }

    fun addSource(source: SourceItem) {
        _sourceItems.value = _sourceItems.value + source
        if (_selectedSource.value == null) {
            _selectedSource.value = source
        }
    }

    fun selectSource(source: SourceItem?) {
        _selectedSource.value = source
    }

    fun addChatMessage(message: ChatMessage) {
        _chatMessages.value = _chatMessages.value + message
    }

    /**
     * Helper for iOS/Swift to register a listener for screen changes.
     */
    fun setScreenListener(listener: (AppScreen) -> Unit) {
        this.screenListener = listener
        listener(currentScreen.value)
    }

    /**
     * Helper for iOS/Swift to register a listener for event updates.
     */
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
