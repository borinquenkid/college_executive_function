package com.borinquenterrier.cef

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adds a new source, generates calendar events via AI, and notifies listeners.
 * Handles AI configuration check and error reporting.
 */
class SourceAdder(
    private val aiService: AIService,
    private val contextAgent: ContextAgent,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val onEventsAdded: (List<Event>) -> Unit,
    private val onError: (AgentError) -> Unit = {}
) {
    fun addSource(source: SourceItem) {
        scope.launch {
            if (aiService.isConfigured()) {
                try {
                    logger.d("SourceAdder", "Processing new source: ${source.title}")
                    val allEvents = aiService.generateCalendarEvents(source.fragments)
                    onEventsAdded(allEvents)
                    contextAgent.analyzeSource(source)
                } catch (e: Exception) {
                    logger.e("SourceAdder", "Failed to process added source: ${source.title}", e)
                    if (e.message?.startsWith("QuotaExhausted") == true) {
                        onError(AgentError.QuotaExhausted)
                    } else {
                        onError(AgentError.GenericError(e.message ?: "Unknown error"))
                    }
                }
            }
        }
    }
}
