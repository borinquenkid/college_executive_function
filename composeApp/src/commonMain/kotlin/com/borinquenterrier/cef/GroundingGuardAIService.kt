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
        return groundToSource("generateCalendarEvents", sourceText, delegate.generateCalendarEvents(fragments))
    }

    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        return groundToSource("generateStudyPlan", syllabusText, delegate.generateStudyPlan(syllabusText, existingSchedule, preferences))
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

    private fun groundToSource(caller: String, sourceText: String, events: List<Event>): List<Event> {
        val sourceYears = GeminiAIService.extractSourceYears(sourceText)
        val grounded = GeminiAIService.filterToSourceYears(events, sourceYears)
        val dropped = events.size - grounded.size
        if (dropped > 0) {
            logger?.d("GroundingGuard", "$caller: dropped $dropped confabulated event(s) outside source years $sourceYears")
        }
        return grounded
    }
}
