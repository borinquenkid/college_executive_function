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
    private val preferencesRepository: PreferencesRepository? = null,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository? = null
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
    ): List<Event> {
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
        val planEvents =
            aiService.generateStudyPlan(syllabusText, existingScheduleText, preferences)

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

        // Step 1: same submission-canonical title + date → prefer TimeEvent over DayEvent.
        // submissionCanonical strips leading verbs ("submit", "complete") then leading
        // possessives ("your") so that "Submit Issue Brief #2 draft…" and "Submit your
        // Issue Brief #2 draft…" both normalize to "issue brief #2 draft…" and collapse
        // when they land on the same date from different extraction batches.
        val preferTimed = extracted
            .groupBy { "${submissionCanonical(it.title)}-${dateOf(it)}" }
            .values
            .map { group -> group.maxByOrNull { if (it is TimeEvent) 1 else 0 }!! }

        // Step 2: same date, 12-char common prefix → same event. Keep the longer (more descriptive)
        // title. Category is intentionally NOT a constraint here — assignment-table extractions
        // (DEADLINE) and online-activity extractions (FINALS) of the same graded submission both land
        // on the same day and must collapse (e.g. "Issue Brief #3: Connecting Hidden Systems" DEADLINE
        // on Jul 22 vs "Submit Issue Brief #3 (final draft)" FINALS on Jul 22).
        val sameDateDeduped = dedupByCommonTitlePrefix(preferTimed)

        // Step 3: cross-date dedup within 7 days for "submit/complete X" vs "X".
        // When the assignment table (using the Wednesday week-anchor heuristic) creates an event 2
        // days before the online-activities section's explicit Friday due date, both survive Step 2
        // because they're on different dates. Normalise by stripping the "submit"/"complete" prefix,
        // then collapse pairs within one calendar week, keeping the later (authoritative) date.
        val crossDateDeduped = dedupSubmissionPairs(sameDateDeduped)

        // Assign deterministic IDs based on content so duplicates are recognized across generations
        return crossDateDeduped.map { event ->
            if (event.id == null) {
                val idContent =
                    "${event.title}|${dateOf(event)}|${if (event is TimeEvent) event.startTime else ""}|${event.category}"
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

    private fun canonicalTitle(title: String): String =
        title.trim().lowercase()
            .replace(Regex("^your\\s+"), "")

    private fun submissionCanonical(title: String): String =
        canonicalTitle(title)
            .replace(Regex("^(submit|complete|upload|post)\\s+"), "")
            .replace(Regex("^your\\s+"), "")

    private fun dedupByCommonTitlePrefix(events: List<Event>): List<Event> {
        val toRemove = mutableSetOf<Event>()
        for (i in events.indices) {
            if (events[i] in toRemove) continue
            for (j in i + 1 until events.size) {
                if (events[j] in toRemove) continue
                val a = events[i]; val b = events[j]
                if (dateOf(a) != dateOf(b)) continue
                val aTitle = canonicalTitle(a.title)
                val bTitle = canonicalTitle(b.title)
                val prefixLen = commonPrefixLength(aTitle, bTitle)
                if (prefixLen >= 12) {
                    val discard = if (aTitle.length <= bTitle.length) a else b
                    toRemove.add(discard)
                }
            }
        }
        return events.filter { it !in toRemove }
    }

    private fun dedupSubmissionPairs(events: List<Event>): List<Event> {
        val toRemove = mutableSetOf<Event>()
        val sorted = events.sortedBy { dateOf(it) }
        for (i in sorted.indices) {
            if (sorted[i] in toRemove) continue
            val a = sorted[i]
            val aCanon = submissionCanonical(a.title)
            val aDate = dateOf(a)
            for (j in i + 1 until sorted.size) {
                if (sorted[j] in toRemove) continue
                val b = sorted[j]
                val bDate = dateOf(b)
                val daysDiff = (bDate.toEpochDays() - aDate.toEpochDays()).toInt()
                if (daysDiff > 7) break
                val bCanon = submissionCanonical(b.title)
                // Require one canonical to be a COMPLETE prefix of the other and end at a
                // word boundary — prevents "IB#2 draft" (Jul 12) matching "IB#2 due" (Jul 15)
                // despite sharing the 16-char prefix "issue brief #2 d".
                val shorter = if (aCanon.length <= bCanon.length) aCanon else bCanon
                val longer = if (aCanon.length > bCanon.length) aCanon else bCanon
                val completePrefix = shorter.length >= 12 &&
                    longer.startsWith(shorter) &&
                    (longer.length == shorter.length || !longer[shorter.length].isLetterOrDigit())
                if (completePrefix) {
                    // Same assignment at different specificity — drop the earlier (week-anchor) date
                    toRemove.add(a)
                }
            }
        }
        return sorted.filter { it !in toRemove }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        for (i in 0 until limit) { if (a[i] != b[i]) return i }
        return limit
    }

    private fun dateOf(event: Event) = when (event) {
        is DayEvent -> event.date
        is TimeEvent -> event.date
    }

    private fun generateDeterministicId(content: String): String {
        return content.encodeUtf8().sha256().hex().take(24)
    }
}
