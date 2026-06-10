package com.borinquenterrier.cef

/**
 * Outermost decorator in the AIService chain. Cross-checks every AI-generated event list
 * against a fact extracted independently from the same source text — the years actually
 * mentioned in it — and drops anything that doesn't match.
 *
 * This is deliberately the *last* link in the chain (wrapping CriticActorAIService and
 * everything inside it): no matter how many generation or critique passes happen internally,
 * exactly one deterministic, non-AI check runs on whatever finally comes out. A model
 * "correcting" an event onto a date the source never mentions is just as ungrounded as
 * inventing it from nothing — the guard can't be skipped or duplicated by accident because
 * there's only one place it lives.
 */
class GroundingGuardAIService(
    private val delegate: AIService,
    private val logger: Logger? = null
) : AIService by delegate {

    override suspend fun generateCalendarEvents(fragments: List<SourceFragment>): List<Event> {
        val sourceText = fragments.joinToString("\n\n") { it.text }
        return groundToSource(
            "generateCalendarEvents",
            sourceText,
            delegate.generateCalendarEvents(fragments)
        )
    }

    override suspend fun generateStudyPlan(
        syllabusText: String,
        existingSchedule: String,
        preferences: StudyPreferences
    ): List<Event> {
        return groundToSource(
            "generateStudyPlan",
            syllabusText,
            delegate.generateStudyPlan(syllabusText, existingSchedule, preferences)
        )
    }

    private fun groundToSource(
        caller: String,
        sourceText: String,
        events: List<Event>
    ): List<Event> {
        val sourceYears = GeminiAIService.extractSourceYears(sourceText)
        val grounded = GeminiAIService.filterToSourceYears(events, sourceYears)
        val dropped = events.size - grounded.size
        if (dropped > 0) {
            logger?.d(
                "GroundingGuard",
                "$caller: dropped $dropped confabulated event(s) outside source years $sourceYears"
            )
        }
        return grounded
    }
}
