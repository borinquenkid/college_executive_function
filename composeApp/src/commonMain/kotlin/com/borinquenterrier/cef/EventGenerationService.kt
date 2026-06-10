package com.borinquenterrier.cef

import java.security.MessageDigest

/**
 * Generates candidate calendar events from a source via AI — either deliverables
 * extracted directly from the document, or a proactive study plan that schedules
 * around the student's existing calendar and preferences. Both paths normalize and
 * de-duplicate the AI's output the same way, which is why they live together.
 */
class EventGenerationService(
    private val aiService: AIService,
    private val normalizationService: NormalizationService,
    private val syllabusAuditor: SyllabusAuditor,
    private val preferencesRepository: PreferencesRepository? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null
) {
    /**
     * Extracts deliverables from [source]'s full text, auditing syllabi for ambiguities
     * first and appending any findings as warnings on the generated events.
     */
    suspend fun extractDeliverables(source: SourceItem): List<Event> {
        val syllabusText = source.fragments.joinToString("\n\n") { it.text }
        val auditWarnings = if (source.category == SourceCategory.SYLLABUS) {
            syllabusAuditor.audit(syllabusText)
        } else {
            emptyList()
        }

        val allEvents = aiService.generateCalendarEvents(source.fragments)
        val normalized = normalize(allEvents)

        return if (auditWarnings.isNotEmpty()) {
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
    }

    /**
     * Generates a study plan for [source], scheduling around [existingEvents] and any
     * user preference constraints so the AI avoids proposing colliding study blocks.
     */
    suspend fun generateStudyPlan(source: SourceItem, existingEvents: List<Event>): List<Event> {
        val syllabusText = source.fragments.joinToString("\n\n") { it.text }
        val existingScheduleText = buildScheduleContext(existingEvents)

        val preferences = preferencesRepository?.getPreferences() ?: StudyPreferences()
        val planEvents = aiService.generateStudyPlan(syllabusText, existingScheduleText, preferences)

        return normalize(planEvents)
    }

    private suspend fun buildScheduleContext(existingEvents: List<Event>): String {
        var scheduleText = existingEvents.joinToString("\n") { event ->
            when (event) {
                is TimeEvent -> "- ${event.title} on ${event.date} from ${event.startTime} to ${event.endTime}"
                is DayEvent -> "- ${event.title} on ${event.date} (All Day)"
            }
        }

        val userConstraints = userPreferenceMemoryRepository?.getDerivedConstraints() ?: emptyList()
        if (userConstraints.isNotEmpty()) {
            val formatHour = { hour: Int -> "${hour.toString().padStart(2, '0')}:00" }
            val constraintsStr = userConstraints.joinToString("\n") {
                "- Restricted: DO NOT schedule any STUDY_BLOCK on ${it.dayOfWeek} from ${formatHour(it.startHour)} to ${formatHour(it.endHour)}"
            }
            scheduleText += "\n\nUser Preference Constraints (strictly avoid scheduling study blocks here):\n$constraintsStr"
        }
        return scheduleText
    }

    private fun normalize(events: List<Event>): List<Event> {
        val normalized = normalizationService.extract(events).distinctBy {
            "${it.title}-${it.date}-${if (it is TimeEvent) it.startTime else ""}"
        }
        // Assign deterministic IDs based on content so duplicates are recognized across generations
        return normalized.map { event ->
            if (event.id == null) {
                val idContent = "${event.title}|${event.date}|${if (event is TimeEvent) event.startTime else ""}|${event.category}"
                val deterministicId = generateDeterministicId(idContent)
                when (event) {
                    is TimeEvent -> event.copy(id = deterministicId)
                    is DayEvent -> event.copy(id = deterministicId)
                }
            } else {
                event
            }
        }
    }

    private fun generateDeterministicId(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.take(12).joinToString("") { "%02x".format(it) }
    }
}
