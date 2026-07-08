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
import kotlin.time.Clock

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
    private val chatRepository = container.chatRepository

    // Chat State - wrapped for testability. Seeded with the in-memory greeting (never persisted);
    // replaced by persisted history on init if any exists.
    private val _chatMessagesWrapper: MutableStateFlowWrapper<List<ChatMessage>> =
        mutableStateFlowWrapper(listOf(ChatMessage.greeting()))
    val chatMessages: StateFlowReader<List<ChatMessage>> = _chatMessagesWrapper

    // The conversation whose messages [chatMessages] currently reflects.
    private val _currentConversationIdWrapper: MutableStateFlowWrapper<String> =
        mutableStateFlowWrapper(ChatMessage.DEFAULT_CONVERSATION_ID)
    val currentConversationId: StateFlowReader<String> = _currentConversationIdWrapper

    // All persisted conversations, most-recently-updated first (drives the management drawer).
    private val _conversationsWrapper: MutableStateFlowWrapper<List<Conversation>> =
        mutableStateFlowWrapper(emptyList())
    val conversations: StateFlowReader<List<Conversation>> = _conversationsWrapper

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
        // Restore persisted chat history so conversations survive app restart. The greeting seed
        // stays only when there is no saved history (an empty DB read is a no-op).
        loadChatHistory()
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

    /**
     * Hydrates the conversation list and the active conversation's messages on startup so chats
     * survive app restart. Ensures a default conversation exists so the drawer is never empty.
     */
    fun loadChatHistory() {
        scope.launch {
            runCatching {
                chatRepository.ensureConversation(
                    ChatMessage.DEFAULT_CONVERSATION_ID,
                    Conversation.DEFAULT_CONVERSATION_TITLE
                )
                refreshConversations()
                loadMessages(_currentConversationIdWrapper.value)
            }
        }
    }

    private suspend fun refreshConversations() {
        _conversationsWrapper.setValue(chatRepository.getConversations())
    }

    /** Loads [conversationId]'s messages, falling back to the greeting empty-state when none exist. */
    private suspend fun loadMessages(conversationId: String) {
        val persisted = chatRepository.getMessages(conversationId)
        _chatMessagesWrapper.setValue(
            persisted.ifEmpty { listOf(ChatMessage.greeting(conversationId)) }
        )
    }

    /** Creates a new empty conversation and switches to it. */
    fun newConversation(createdAt: Long = Clock.System.now().toEpochMilliseconds()) {
        scope.launch {
            val conversation = Conversation.create(createdAt)
            runCatching {
                chatRepository.createConversation(conversation)
                _currentConversationIdWrapper.setValue(conversation.id)
                refreshConversations()
                loadMessages(conversation.id)
            }
        }
    }

    /** Switches the active conversation and loads its messages. */
    fun selectConversation(id: String) {
        scope.launch {
            _currentConversationIdWrapper.setValue(id)
            runCatching { loadMessages(id) }
        }
    }

    /** Renames a conversation; the drawer list refreshes to reflect the new title/order. */
    fun renameConversation(
        id: String,
        title: String,
        updatedAt: Long = Clock.System.now().toEpochMilliseconds()
    ) {
        scope.launch {
            runCatching {
                chatRepository.renameConversation(id, title, updatedAt)
                refreshConversations()
            }
        }
    }

    /** Repins a conversation's source scope (per-chat "All" vs a single source). */
    fun setConversationSourceScope(
        id: String,
        scope: ChatSourceScope,
        updatedAt: Long = Clock.System.now().toEpochMilliseconds()
    ) {
        this.scope.launch {
            runCatching {
                chatRepository.setSourceScope(id, scope, updatedAt)
                refreshConversations()
            }
        }
    }

    /**
     * Deletes a conversation and its messages. When the active conversation is deleted, switches to
     * the most-recent remaining one, recreating the default conversation if none remain.
     */
    fun deleteConversation(id: String) {
        scope.launch {
            runCatching {
                chatRepository.deleteConversation(id)
                refreshConversations()
                if (_currentConversationIdWrapper.value != id) return@runCatching
                val next = _conversationsWrapper.value.firstOrNull()
                if (next != null) {
                    _currentConversationIdWrapper.setValue(next.id)
                    loadMessages(next.id)
                } else {
                    chatRepository.ensureConversation(
                        ChatMessage.DEFAULT_CONVERSATION_ID,
                        Conversation.DEFAULT_CONVERSATION_TITLE
                    )
                    refreshConversations()
                    _currentConversationIdWrapper.setValue(ChatMessage.DEFAULT_CONVERSATION_ID)
                    loadMessages(ChatMessage.DEFAULT_CONVERSATION_ID)
                }
            }
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

    /** Clears the calendar locally AND on Google Calendar (via the resilient cleaner), plus UI state. */
    fun resetCalendar() {
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
     * Re-extracts every registered source to rebuild the calendar — e.g. after a reset, which
     * clears events but keeps sources. Extraction is served from the analysis cache (unchanged
     * content), so this is fast and cheap. Restores deliverables/class sessions; the study plan
     * stays on-demand.
     */
    fun rebuildFromSources() {
        launchInScope {
            val sources = container.sourceLoader.loadSources()
            for (source in sources) {
                sourceManager.registerSource(source)
                sourceProcessingMutex.withLock {
                    try {
                        container.harnessSourceProcessor.processSource(source)
                    } catch (e: Exception) {
                        container.logger.e("AppController", "Rebuild failed for ${source.title}: ${e.message}", e)
                    }
                }
            }
        }
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
        // The greeting is a non-persisted empty-state; drop it once a real turn arrives so it never
        // lingers above the conversation or gets confused for history.
        val base = _chatMessagesWrapper.value.filterNot { it.id == ChatMessage.GREETING_ID }
        _chatMessagesWrapper.setValue(base + message)
        // Persist off the UI update; a write failure must never lose the on-screen message.
        scope.launch {
            runCatching {
                chatRepository.saveMessage(message, TokenEstimator.estimate(message.content).toLong())
                deriveTitleFromFirstUserMessage(message)
                refreshConversations()
            }
        }
    }

    /**
     * Names a still-untitled conversation from its first user turn. Only fires while the title is a
     * placeholder, so a user-chosen title is never overwritten.
     */
    private suspend fun deriveTitleFromFirstUserMessage(message: ChatMessage) {
        if (message.role != ChatRole.USER) return
        val conversation = chatRepository.getConversation(message.conversationId) ?: return
        val isPlaceholder = conversation.title == Conversation.DEFAULT_TITLE ||
            conversation.title == Conversation.DEFAULT_CONVERSATION_TITLE
        if (!isPlaceholder) return
        chatRepository.renameConversation(
            message.conversationId,
            ConversationTitle.fromFirstMessage(message.content),
            message.createdAt
        )
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
