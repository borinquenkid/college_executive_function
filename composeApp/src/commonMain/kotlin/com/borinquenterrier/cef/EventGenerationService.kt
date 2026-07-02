package com.borinquenterrier.cef

import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository = UserPreferenceMemoryRepository.NoOp,
    // Optional analysis cache: skips the (expensive) LLM extraction + audit for a source whose
    // content we have already analyzed. Shared by every extractDeliverables caller. Null disables it.
    private val cacheRepository: AnalysisCacheRepository? = null,
    private val clock: Clock = Clock.System
) {
    private companion object {
        // Bump when the post-LLM pipeline (normalize/dedup/prefix-strip) changes, so old cache
        // entries — cached in the previous format — are ignored instead of served stale.
        const val GENERATION_CACHE_VERSION = 1
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
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
        val prefs = preferencesRepository.getPreferences()

        // Cache hit: reuse the previously analyzed events (skips the LLM extraction + audit), but
        // re-apply the semester window since that config can change between runs.
        val cacheKey = generationCacheKey(source.fragments)
        readCachedEvents(cacheKey)?.let { cachedEvents ->
            setAttribute("events.cache_hit", "true")
            onProgress?.invoke("Using cached analysis.")
            return@span SemesterFilter.apply(cachedEvents, prefs)
        }
        setAttribute("events.cache_hit", "false")

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

        val withWarnings = if (auditWarnings.isNotEmpty()) {
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

        // Cache the content-deterministic result (post-normalize, incl. audit warnings) BEFORE the
        // semester filter, so a config change is reflected on the next call without a re-run.
        writeCachedEvents(cacheKey, withWarnings)

        // Constrain to the active semester window at the generation choke point so EVERY caller
        // (studio staging AND the auto-push pipeline) drops out-of-term events before they can be
        // pushed to the calendar — not just hidden in the view.
        val inSemester = SemesterFilter.apply(withWarnings, prefs)
        setAttribute("events.extracted_count", inSemester.size.toLong())
        // Tag with the originating source so deleting that source removes its events and the
        // reconciler can spot orphans (events whose source no longer exists).
        inSemester.map { it.withSourceId(source.title) }
    }

    private fun generationCacheKey(fragments: List<SourceFragment>): String =
        "${ContentHasher.hash(fragments)}-v$GENERATION_CACHE_VERSION"

    private suspend fun readCachedEvents(key: String): List<Event>? {
        val cached = cacheRepository?.getCached(key) ?: return null
        if (clock.now().toEpochMilliseconds() - cached.createdAt > CACHE_TTL_MS) return null
        if (cached.cachedEventsJson.isBlank()) return null
        return runCatching { Json.decodeFromString<List<Event>>(cached.cachedEventsJson) }.getOrNull()
    }

    private suspend fun writeCachedEvents(key: String, events: List<Event>) {
        val repo = cacheRepository ?: return
        repo.putCache(
            CachedAnalysis(
                sourceHash = key,
                cachedEventsJson = Json.encodeToString(events),
                cachedMetadataJson = null,
                createdAt = clock.now().toEpochMilliseconds()
            )
        )
    }

    /**
     * Generates a study plan for [source], scheduling around [existingEvents] and any
     * user preference constraints so the AI avoids proposing colliding study blocks.
     */
    suspend fun generateStudyPlan(source: SourceItem, existingEvents: List<Event>): StudyPlanResult =
        AppTracer.current.span(
            "events.generate_study_plan",
            mapOf("source.title" to source.title, "calendar.existing_count" to existingEvents.size.toString())
        ) {
            val syllabusText = source.fragments.joinToString("\n\n") { it.text }
            val existingScheduleText = buildScheduleContext(existingEvents)

            val preferences = preferencesRepository.getPreferences()
            val planEvents = aiService.generateStudyPlan(syllabusText, existingScheduleText, preferences)

            // Catch confabulation inside the pipeline: split into push-ready events and a
            // needs-date-resolution channel (the latter is surfaced to the date-picker dialog).
            val result = StudyPlanResolver.resolve(normalize(planEvents), syllabusText)
            setAttribute("events.planned_count", result.grounded.size.toLong())
            setAttribute("events.needs_resolution_count", result.needsResolution.size.toLong())
            result.copy(grounded = result.grounded.map { it.withSourceId(source.title) })
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
        // Strip any LLM-leaked category label ("DEADLINE: …", "STUDY_BLOCK: …") from the display
        // title before dedup + id generation, so it never persists and dedup compares clean titles.
        val cleaned = events.map { event ->
            val clean = EventDeduplicator.stripLeakedCategoryPrefix(event.title)
            if (clean == event.title) event else when (event) {
                is TimeEvent -> event.copy(title = clean)
                is DayEvent -> event.copy(title = clean)
            }
        }
        val extracted = normalizationService.extract(cleaned)
        val deduped = EventDeduplicator.dedup(extracted)
        val now = clock.now().toEpochMilliseconds()
        return deduped.map { event ->
            val ided = if (event.id == null) {
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
            // Stamp a creation timestamp so sync doesn't perpetually treat freshly-generated events
            // (updatedAt=0) as older than their remote copies and overwrite them forever.
            if (ided.updatedAt == 0L) ided.withUpdatedAt(now) else ided
        }
    }

    private fun generateDeterministicId(content: String): String {
        return content.encodeUtf8().sha256().hex().take(24)
    }
}
