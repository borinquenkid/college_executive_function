package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.borinquenterrier.cef.db.AppDatabase

/**
 * Encapsulates the business logic for the Studio panel, 
 * separating AI processing and event management from the UI.
 */
class EventAgent(
    private val aiService: AIService,
    private val repository: CalendarAgent,
    private val database: AppDatabase? = null,
    private val normalizationService: NormalizationService = NormalizationService(),
    private val logger: Logger? = null
) {
    private val tag = "EventAgent"

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
            val allEvents = aiService.generateCalendarEvents(source.fragments)
            
            // De-duplicate events by properties (title, date, time)
            val processed = normalizationService.extract(allEvents).distinctBy { 
                "${it.title}-${it.date}-${if (it is TimeEvent) it.startTime else ""}"
            }
            
            _lastGeneratedEvents.value = processed
            _statusMessage.value = "${processed.size} deadlines and exams found from entire source."
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
        _statusMessage.value = "Planning study time from full context..."
        
        try {
            // Join parts for study plan logic
            val syllabusText = source.fragments.joinToString("\n\n") { it.text }
            val planEvents = aiService.generateStudyPlan(syllabusText)
            
            val processed = normalizationService.extract(planEvents).distinctBy { 
                "${it.title}-${it.date}-${if (it is TimeEvent) it.startTime else ""}"
            }
            
            _lastGeneratedEvents.value = processed
            _statusMessage.value = "${processed.size} events planned for study time."
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
