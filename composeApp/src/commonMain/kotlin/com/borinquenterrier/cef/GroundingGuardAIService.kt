package com.borinquenterrier.cef

/**
 * Outermost decorator in the AIService chain. Cross-checks AI-generated output against
 * facts extracted independently from the same source text.
 *
 * **Year-level grounding** — events are filtered to years explicitly mentioned in the
 * source text. An event dated 2099 from a source that only mentions 2026 is a confabulation
 * and is dropped. A student may load syllabi from any semester (past, current, or future)
 * and all events grounded in that source's years are kept.
 *
 * **Source-fact grounding** — for free-text coaching responses (generateChatResponse),
 * specific date and grade-weight claims that do not appear anywhere in the provided context
 * are flagged with a verification warning appended to the response.
 *
 * This is deliberately the *last* link in the chain (wrapping CriticActorAIService and
 * everything inside it): no matter how many generation or critique passes happen internally,
 * exactly one deterministic, non-AI check runs on whatever finally comes out.
 */
class GroundingGuardAIService(
    private val delegate: AIService,
    private val logger: Logger? = null
) : AIService by delegate {

    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val sourceText = fragments.joinToString("\n\n") { it.text }
        return AppTracer.current.span(
            "pipeline.generate_calendar_events",
            mapOf(
                "fragment.count" to fragments.size.toString(),
                "fragment.chars" to fragments.sumOf { it.text.length }.toString()
            )
        ) {
            val events = delegate.generateCalendarEvents(fragments)
            val result = groundToSource("generateCalendarEvents", sourceText, events, spanScope = this)
            setAttribute("events.final", result.size.toLong())
            setAttribute("class.final", result.count { it.category == AcademicCategory.CLASS }.toLong())
            result
        }
    }

    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        return AppTracer.current.span("grounding.study_plan") {
            val raw = delegate.generateStudyPlan(syllabusText, existingSchedule, preferences)
            // 1) Year-level grounding (drops events outside the source's years).
            val yearGrounded = groundToSource("generateStudyPlan", syllabusText, raw, spanScope = this)
            // 2) Date-in-source grounding: a DEADLINE/FINALS whose date is nowhere in the syllabus
            //    is confabulated. Dropped here; the ungrounded set is recorded so a future
            //    date-picker dialog can resurface it instead of losing it.
            val dateClass = SourceDateGrounder.classifyDeliverables(yearGrounded, syllabusText)
            // 3) Anchor grounding: a STUDY_BLOCK must prepare for a *surviving* deliverable.
            //    Removing fabricated-date deliverables above orphans any block that only prepped
            //    for them, so those blocks fall away here too.
            val anchored = StudyPlanGrounder.ground(dateClass.grounded)

            setAttribute("events.before", raw.size.toLong())
            setAttribute("events.after", anchored.grounded.size.toLong())
            setAttribute("deliverables.ungrounded_dropped", dateClass.ungrounded.size.toLong())
            setAttribute(
                "study_blocks.before",
                yearGrounded.count { it.category == AcademicCategory.STUDY_BLOCK }.toLong()
            )
            setAttribute(
                "study_blocks.after",
                anchored.grounded.count { it.category == AcademicCategory.STUDY_BLOCK }.toLong()
            )
            setAttribute("orphans.dropped", anchored.droppedOrphanStudyBlocks.toLong())
            if (anchored.droppedOrphanStudyBlocks > 0) {
                logger?.d(
                    "GroundingGuard",
                    "study plan: dropped ${anchored.droppedOrphanStudyBlocks} ungrounded STUDY_BLOCK(s) with no deliverable to prep for"
                )
            }
            anchored.grounded
        }
    }

    // The prompt already contains the injected source fragments (via ContextAgent), so the
    // prompt itself is the ground truth — anything claimed that isn't in the prompt was not
    // in the provided context and is therefore ungrounded.
    override suspend fun generateChatResponse(prompt: String): String {
        val response = delegate.generateChatResponse(prompt)
        return SourceFactGrounder.groundFreeText(response, prompt, logger)
    }

    // analyzeDocument returns structured JSON metadata (grading scale, late policy, etc.)
    // and is not a user-facing coaching response. Source-fact grounding is not applied here
    // because appending warning text would corrupt the JSON format the caller expects.

    private fun groundToSource(
        caller: String,
        sourceText: String,
        events: List<Event>,
        spanScope: SpanScope? = null
    ): List<Event> {
        val sourceYears = GeminiAIService.extractSourceYears(sourceText)
        val grounded = GeminiAIService.filterToSourceYears(events, sourceYears)
        val dropped = events.size - grounded.size

        val categoriesBefore = events.groupBy { it.category.name }.mapValues { it.value.size }
        val categoriesAfter = grounded.groupBy { it.category.name }.mapValues { it.value.size }

        val eventAttrs = mapOf(
            "caller" to caller,
            "source.years" to sourceYears.sorted().joinToString(),
            "events.before" to events.size.toString(),
            "events.after" to grounded.size.toString(),
            "events.dropped" to dropped.toString(),
            "categories.before" to categoriesBefore.entries.joinToString { "${it.key}=${it.value}" },
            "categories.after" to categoriesAfter.entries.joinToString { "${it.key}=${it.value}" },
            "class.before" to (categoriesBefore["CLASS"] ?: 0).toString(),
            "class.after" to (categoriesAfter["CLASS"] ?: 0).toString(),
        )
        // Use direct span reference when available (coroutine-safe); fall back to thread-local.
        spanScope?.addEvent("grounding.filter", eventAttrs)
            ?: AppTracer.current.event("grounding.filter", eventAttrs)

        if (dropped > 0) {
            logger?.d("GroundingGuard", "$caller: dropped $dropped confabulated event(s) outside source years $sourceYears")
        }
        return grounded
    }
}
