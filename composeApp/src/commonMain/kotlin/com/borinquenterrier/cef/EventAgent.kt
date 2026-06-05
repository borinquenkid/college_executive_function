package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.LocalDate
import com.borinquenterrier.cef.db.AppDatabase
import kotlinx.serialization.json.*

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
    private val logger: Logger? = null
) {
    private val tag = "EventAgent"

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

    fun updateStatus(message: String) {
        _statusMessage.value = message
    }

    /**
     * Extracts standard deliverables from a source using AI, processing the full context at once.
     */
    suspend fun extractDeliverables(source: SourceItem) {
        _isLoading.value = true
        _statusMessage.value = "Analyzing ${source.title}..."
        
        try {
            val syllabusText = source.fragments.joinToString("\n\n") { it.text }
            val auditWarnings = if (source.category == SourceCategory.SYLLABUS) {
                auditSyllabus(syllabusText)
            } else {
                emptyList()
            }

            val allEvents = aiService.generateCalendarEvents(source.fragments)
            
            // De-duplicate events by properties (title, date, time)
            val normalized = normalizationService.extract(allEvents).distinctBy { 
                "${it.title}-${it.date}-${if (it is TimeEvent) it.startTime else ""}"
            }

            val processed = if (auditWarnings.isNotEmpty()) {
                val combinedWarning = auditWarnings.joinToString("; ")
                normalized.map { event ->
                    val newWarning = if (event.warning != null) {
                        "${event.warning}; $combinedWarning"
                    } else {
                        combinedWarning
                    }
                    when (event) {
                        is TimeEvent -> event.copy(warning = newWarning)
                        is DayEvent -> event.copy(warning = newWarning)
                    }
                }
            } else {
                normalized
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

    private suspend fun auditSyllabus(text: String): List<String> {
        val prompt = AiPrompts.getSyllabusAuditPrompt(text)
        val response = aiService.generateChatResponse(prompt)
        return try {
            val cleanJson = response.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            val root = Json.parseToJsonElement(cleanJson).jsonObject
            val hasAmbiguities = root["hasAmbiguities"]?.jsonPrimitive?.booleanOrNull ?: false
            if (hasAmbiguities) {
                root["findings"]?.jsonArray?.map { element ->
                    val obj = element.jsonObject
                    val desc = obj["description"]?.jsonPrimitive?.content ?: "Syllabus ambiguity detected"
                    val type = obj["type"]?.jsonPrimitive?.content ?: "GENERAL"
                    "[$type] $desc"
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger?.e(tag, "Failed to parse syllabus audit response: $response", e)
            emptyList()
        }
    }

    /**
     * Generates a proactive study plan, using the full document context and existing calendar events.
     */
    suspend fun generateStudyPlan(source: SourceItem) {
        _isLoading.value = true
        _statusMessage.value = "Planning study time from full context..."
        
        try {
            // Join parts for study plan logic
            val syllabusText = source.fragments.joinToString("\n\n") { it.text }
            
            // Get existing events to prevent collisions
            val existingEvents = repository.getEvents("default")
            val existingScheduleText = existingEvents.joinToString("\n") { event ->
                when (event) {
                    is TimeEvent -> "- ${event.title} on ${event.date} from ${event.startTime} to ${event.endTime}"
                    is DayEvent -> "- ${event.title} on ${event.date} (All Day)"
                }
            }
            
            val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
            val planEvents = aiService.generateStudyPlan(syllabusText, existingScheduleText, preferences)
            
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
     * Returns a list of events that COULD NOT be pushed due to overlaps.
     */
    suspend fun pushToCalendar(calendarId: String = "default"): List<Event> {
        val events = _lastGeneratedEvents.value
        if (events.isEmpty()) return emptyList()

        _isLoading.value = true
        _statusMessage.value = "Syncing ${events.size} events to your calendar..."
        
        val conflicts = mutableListOf<Event>()
        var successCount = 0
        
        try {
            val existing = repository.getEvents(calendarId)
            val currentCalendarState = existing.toMutableList()
            
            val resolvedList = mutableListOf<Event>()
            val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
            val resolver = CollisionResolver(preferences = preferences)
            
            for (event in events) {
                val result = resolver.resolve(event, currentCalendarState)
                when (result) {
                    is ResolutionResult.Success -> {
                        // Update currentCalendarState so subsequent events in this batch resolve against it
                        for (resolved in result.resolvedEvents) {
                            resolved.id?.let { id ->
                                currentCalendarState.removeAll { it.id == id }
                            }
                            currentCalendarState.add(resolved)
                        }
                        resolvedList.addAll(result.resolvedEvents)
                    }
                    is ResolutionResult.Conflict -> {
                        conflicts.add(event)
                    }
                }
            }

            // Save all successfully resolved events (both new and bumped)
            for (resolved in resolvedList) {
                try {
                    repository.saveEvent(resolved, calendarId)
                    successCount++
                } catch (e: OverlapException) {
                    logger?.d(tag, "Conflict detected for: ${resolved.title}")
                    conflicts.add(resolved)
                }
            }
            
            if (conflicts.isEmpty()) {
                _statusMessage.value = "Success! All $successCount events pushed."
                _lastGeneratedEvents.value = emptyList()
            } else {
                _statusMessage.value = "Synced $successCount events. ${conflicts.size} conflicts need review."
                _lastGeneratedEvents.value = conflicts
            }
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
        _isLoading.value = true
        _decompositionTarget.value = event
        _decomposedTasks.value = emptyList()
        _statusMessage.value = "Breaking down '${event.title}'..."

        try {
            val tasks = aiService.decomposeTask(event.title, event.date.toString())
            _decomposedTasks.value = tasks
            _statusMessage.value = "${tasks.size} steps created."
        } catch (e: Exception) {
            logger?.e(tag, "Error decomposing task", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun acceptDecomposition(calendarId: String = "default") {
        val tasks = _decomposedTasks.value
        val target = _decompositionTarget.value ?: return
        if (tasks.isEmpty()) return

        _isLoading.value = true
        _statusMessage.value = "Adding ${tasks.size} steps to calendar..."

        var count = 0
        try {
            // Find earliest task date
            val earliestTaskDate = tasks.minOfOrNull {
                target.date.minus(it.daysBeforeDue, DateTimeUnit.DAY)
            }

            // Update target event's studyPlanStart and save it back
            val updatedTarget = when (target) {
                is TimeEvent -> target.copy(studyPlanStart = earliestTaskDate?.toString())
                is DayEvent -> target.copy(studyPlanStart = earliestTaskDate?.toString())
            }
            repository.updateEvent(updatedTarget, calendarId)

            for (task in tasks) {
                val taskDate = target.date.minus(task.daysBeforeDue, DateTimeUnit.DAY)
                val event = DayEvent(
                    title = task.title,
                    source = EventSource.AI_GENERATED,
                    category = AcademicCategory.STUDY_BLOCK,
                    date = taskDate
                )
                try {
                    repository.saveEvent(event, calendarId)
                    count++
                } catch (e: OverlapException) {
                    // Skip conflicting steps and continue
                }
            }
            _statusMessage.value = "$count steps added to calendar."
            clearDecomposition()
        } catch (e: Exception) {
            logger?.e(tag, "Error accepting decomposition", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
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
        _isLoading.value = true
        try {
            val updated = when (event) {
                is TimeEvent -> event.copy(completionStatus = CompletionStatus.COMPLETED)
                is DayEvent -> event.copy(completionStatus = CompletionStatus.COMPLETED)
            }
            repository.updateEvent(updated, "default")
            _statusMessage.value = "Marked '${event.title}' as completed."
            loadIncompleteEvents()
            try {
                repository.synchronize("default")
            } catch (e: Exception) {
                // Ignore sync errors
            }
        } catch (e: Exception) {
            logger?.e(tag, "Failed to mark event completed", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun skipEvent(event: Event) {
        _isLoading.value = true
        try {
            val updated = when (event) {
                is TimeEvent -> event.copy(completionStatus = CompletionStatus.SKIPPED)
                is DayEvent -> event.copy(completionStatus = CompletionStatus.SKIPPED)
            }
            repository.updateEvent(updated, "default")
            _statusMessage.value = "Skipped '${event.title}'."
            loadIncompleteEvents()
        } catch (e: Exception) {
            logger?.e(tag, "Failed to skip event", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun rescheduleEvent(event: Event) {
        _isLoading.value = true
        try {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val updated = when (event) {
                is TimeEvent -> event.copy(date = today)
                is DayEvent -> event.copy(date = today)
            }
            val existing = repository.getEvents("default").toMutableList()
            existing.removeAll { it.id == event.id }

            val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
            val resolver = CollisionResolver(preferences = preferences)
            val result = resolver.resolve(updated, existing)

            when (result) {
                is ResolutionResult.Success -> {
                    for (resolved in result.resolvedEvents) {
                        repository.updateEvent(resolved, "default")
                    }
                    _statusMessage.value = "Rescheduled '${event.title}' successfully."
                }
                is ResolutionResult.Conflict -> {
                    _statusMessage.value = "Cannot reschedule: conflict detected."
                }
            }
            loadIncompleteEvents()
        } catch (e: Exception) {
            logger?.e(tag, "Failed to reschedule event", e)
            _statusMessage.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}
