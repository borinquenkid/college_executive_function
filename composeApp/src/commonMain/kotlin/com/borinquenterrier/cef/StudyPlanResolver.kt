package com.borinquenterrier.cef

/**
 * A deliverable whose date could not be grounded in the source, paired with the source text most
 * relevant to it. Surfaced to the UI so the user can confirm/pick a date (or discard) rather than
 * having it silently dropped. [sourceSnippet] is null when nothing in the source matches the
 * title — a hint that the item is fabricated rather than merely mis-dated.
 */
data class DateResolutionItem(
    val event: Event,
    val sourceSnippet: String?,
)

/**
 * Two-channel result of grounding a study plan: [grounded] events are safe to push;
 * [needsResolution] events were caught by date grounding and require the user to confirm a date
 * before they can be trusted.
 */
data class StudyPlanResult(
    val grounded: List<Event>,
    val needsResolution: List<DateResolutionItem>,
)

/**
 * Turns a (year-grounded) study plan into a [StudyPlanResult]:
 *  1. date-in-source grounding ([SourceDateGrounder]) splits deliverables into grounded vs
 *     ungrounded; the ungrounded ones become [DateResolutionItem]s with a source snippet,
 *  2. anchor grounding ([StudyPlanGrounder]) drops STUDY_BLOCKs left with no surviving
 *     deliverable to prepare for.
 *
 * This is the deterministic "catch it inside the pipeline" step; the policy of presenting
 * [StudyPlanResult.needsResolution] in a date-picker dialog lives in the UI.
 */
object StudyPlanResolver {
    fun resolve(events: List<Event>, sourceText: String): StudyPlanResult {
        val dateClass = SourceDateGrounder.classifyDeliverables(events, sourceText)
        val anchored = StudyPlanGrounder.ground(dateClass.grounded)
        val needsResolution = dateClass.ungrounded.map { event ->
            DateResolutionItem(event, SourceSnippetExtractor.snippet(sourceText, event.title))
        }
        return StudyPlanResult(grounded = anchored.grounded, needsResolution = needsResolution)
    }
}
