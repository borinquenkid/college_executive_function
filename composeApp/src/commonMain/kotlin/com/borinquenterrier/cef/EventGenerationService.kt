package com.borinquenterrier.cef

import okio.ByteString.Companion.encodeUtf8

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
    private val preferencesRepository: PreferencesPort = PreferencesPort.NoOp,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp
) {
    /**
     * Extracts deliverables from [source]'s full text, auditing syllabi for ambiguities
     * first and appending any findings as warnings on the generated events.
     *
     * [onProgress] receives a human-readable status string after each batch so callers
     * can show per-page progress rather than a static spinner.
     */
    suspend fun extractDeliverables(
        source: SourceItem,
        onProgress: ((message: String) -> Unit)? = null
    ): List<Event> = AppTracer.current.span(
        "events.extract_deliverables",
        mapOf("source.title" to source.title, "source.category" to source.category.name)
    ) {
        val syllabusText = source.fragments.joinToString("\n\n") { it.text }
        val auditWarnings = if (source.category == SourceCategory.SYLLABUS) {
            onProgress?.invoke("Auditing source for ambiguities...")
            syllabusAuditor.audit(syllabusText)
        } else {
            emptyList()
        }

        val fragments = source.fragments
        val isText = fragments.firstOrNull()?.type == SourceType.TEXT
        val batches = if (isText && fragments.size > SourceFragmentBatcher.BATCH_SIZE) {
            SourceFragmentBatcher.batch(fragments)
        } else {
            listOf(fragments)
        }

        setAttribute("source.batch_count", batches.size.toLong())
        val allEvents = mutableListOf<Event>()
        batches.forEachIndexed { i, batch ->
            val pageRange = batch.mapNotNull { it.pageNumber }
            val pageLabel = if (pageRange.isNotEmpty()) {
                "pages ${pageRange.min()}–${pageRange.max()}"
            } else {
                "section ${i + 1}"
            }
            onProgress?.invoke("Extracting events from $pageLabel (${i + 1}/${batches.size})...")
            allEvents.addAll(aiService.generateCalendarEvents(batch))
        }

        val normalized = normalize(allEvents)
        setAttribute("events.extracted_count", normalized.size.toLong())

        if (auditWarnings.isNotEmpty()) {
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
    suspend fun generateStudyPlan(source: SourceItem, existingEvents: List<Event>): List<Event> =
        AppTracer.current.span(
            "events.generate_study_plan",
            mapOf("source.title" to source.title, "calendar.existing_count" to existingEvents.size.toString())
        ) {
            val syllabusText = source.fragments.joinToString("\n\n") { it.text }
            val existingScheduleText = buildScheduleContext(existingEvents)

            val preferences = preferencesRepository.getPreferences()
            val planEvents = aiService.generateStudyPlan(syllabusText, existingScheduleText, preferences)

            val normalized = normalize(planEvents)
            setAttribute("events.planned_count", normalized.size.toLong())
            normalized
        }

    private suspend fun buildScheduleContext(existingEvents: List<Event>): String {
        var scheduleText = existingEvents.joinToString("\n") { event ->
            when (event) {
                is TimeEvent -> "- ${event.title} on ${event.date} from ${event.startTime} to ${event.endTime}"
                is DayEvent -> "- ${event.title} on ${event.date} (All Day)"
            }
        }

        val userConstraints = userPreferenceMemoryRepository.getDerivedConstraints()
        if (userConstraints.isNotEmpty()) {
            val formatHour = { hour: Int -> "${hour.toString().padStart(2, '0')}:00" }
            val constraintsStr = userConstraints.joinToString("\n") {
                "- Restricted: DO NOT schedule any STUDY_BLOCK on ${it.dayOfWeek} from ${
                    formatHour(
                        it.startHour
                    )
                } to ${formatHour(it.endHour)}"
            }
            scheduleText += "\n\nUser Preference Constraints (strictly avoid scheduling study blocks here):\n$constraintsStr"
        }
        return scheduleText
    }

    private fun normalize(events: List<Event>): List<Event> {
        val extracted = normalizationService.extract(events)
        val deduped = EventDeduplicator.dedup(extracted)
        return deduped.map { event ->
            if (event.id == null) {
                val idContent =
                    "${event.title}|${EventDeduplicator.dateOf(event)}|${if (event is TimeEvent) event.startTime else ""}|${event.category}"
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
        return content.encodeUtf8().sha256().hex().take(24)
    }
}
