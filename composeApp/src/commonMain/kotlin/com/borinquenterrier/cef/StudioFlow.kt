package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.borinquenterrier.cef.db.AppDatabase

/**
 * Encapsulates the business logic for the Studio panel, 
 * separating AI processing and event management from the UI.
 */
class StudioFlow(
    private val aiService: AIService,
    private val repository: UnifiedCalendarRepository,
    private val database: AppDatabase? = null,
    private val programmaticExtractor: KeywordEventExtractor = KeywordEventExtractor(),
    private val logger: Logger? = null
) {
    private val tag = "StudioFlow"

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("Select a source and an action.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastGeneratedEvents = MutableStateFlow<List<Event>>(emptyList())
    val lastGeneratedEvents: StateFlow<List<Event>> = _lastGeneratedEvents.asStateFlow()

    /**
     * Extracts standard deliverables from a source using AI, processing the full context at once.
     */
    suspend fun extractDeliverables(source: SourceItem) {
        _isLoading.value = true
        _statusMessage.value = "Analyzing ${source.title}..."
        
        try {
            val allEvents = aiService.generateCalendarEvents(source.chunks)
            
            // De-duplicate events by properties (title, date, time)
            val processed = programmaticExtractor.extract(allEvents).distinctBy { 
                "${it.title}-${it.date}-${if (it is TimeEvent) it.startTime else ""}"
            }
            
            _lastGeneratedEvents.value = processed
            _statusMessage.value = "${processed.size} deliverables identified from entire source."
        } catch (e: Exception) {
            logger?.e(tag, "Error extracting deliverables", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Generates a proactive study plan, using the full document context.
     */
    suspend fun generateStudyPlan(source: SourceItem) {
        _isLoading.value = true
        _statusMessage.value = "Generating study plan from full context..."
        
        try {
            // Join chunks for study plan logic
            val syllabusText = source.chunks.joinToString("\n\n") { it.text }
            val planEvents = aiService.generateStudyPlan(syllabusText)
            
            val processed = programmaticExtractor.extract(planEvents).distinctBy { 
                "${it.title}-${it.date}-${if (it is TimeEvent) it.startTime else ""}"
            }
            
            _lastGeneratedEvents.value = processed
            _statusMessage.value = "${processed.size} events generated in study plan."
        } catch (e: Exception) {
            logger?.e(tag, "Error generating study plan", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Pushes the last generated batch of events to the calendar, checking for conflicts.
     */
    suspend fun pushToCalendar(calendarId: String = "default") {
        val events = _lastGeneratedEvents.value
        if (events.isEmpty()) return

        _isLoading.value = true
        _statusMessage.value = "Syncing ${events.size} events to your calendar..."
        
        var successCount = 0
        try {
            for (event in events) {
                try {
                    repository.saveEvent(event, calendarId)
                    successCount++
                } catch (e: OverlapException) {
                    _statusMessage.value = "Conflict: '${event.title}' overlaps with an existing event. Stopping sync."
                    return
                }
            }
            _statusMessage.value = "Success! $successCount events pushed to your calendar."
            _lastGeneratedEvents.value = emptyList() // Clear after successful push
        } catch (e: Exception) {
            logger?.e(tag, "Error pushing to calendar", e)
            _statusMessage.value = "Sync Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun clear() {
        _lastGeneratedEvents.value = emptyList()
        _statusMessage.value = "Select a source and an action."
    }
}
