package com.borinquenterrier.cef

/**
 * [events] are safe to push. [needsResolution] are events held back because the source's own weekday and date
 * contradict each other: the student decides, nothing is guessed.
 */
data class ExtractionResult(
    val events: List<Event>,
    val warning: String?,
    val statusMessage: String,
    val needsResolution: List<DateResolutionItem> = emptyList(),
    /** Events held because the source produced more than [MAX_EVENTS_PER_SOURCE]; pushed only if the student agrees. */
    val heldForSize: List<Event> = emptyList()
)

/**
 * ADR 0015: a source that yields more than this many events is not auto-pushed. Real syllabi hold roughly 10-60,
 * so normal use never reaches it; an injected or malformed document that floods the calendar does.
 */
const val MAX_EVENTS_PER_SOURCE = 150

internal class DeliverableExtractor(
    private val generationService: EventGenerationService,
    private val onProgress: (String) -> Unit = {}
) : AgentAction<SourceItem, ExtractionResult> {

    override suspend fun run(input: SourceItem, calendarId: String): ExtractionResult {
        val outcome = generationService.extractWithOutcome(input) { message -> onProgress(message) }
        val events = outcome.events
        val windowNote = outsideWindowNote(outcome)
        // When the window removed everything, "no events found" would be wrong: the window note says why.
        val base = if (events.isEmpty() && windowNote != null) null else baseWarning(events, input)
        val warning = listOfNotNull(base, windowNote).joinToString(" ").ifEmpty { null }
        val (clean, held) = WeekdayDateConflictDetector.partition(events, input.fragments.joinToString("\n\n") { it.text })
        val heldNote = if (held.isEmpty()) "" else " ${held.size} need your date (the source contradicts itself)."
        if (clean.size > MAX_EVENTS_PER_SOURCE) {
            return ExtractionResult(
                emptyList(), warning,
                "${clean.size} events found, which is unusually many. Waiting for your OK before adding them.$heldNote",
                held, heldForSize = clean
            )
        }
        return ExtractionResult(clean, warning, "${clean.size} deadlines and exams found from entire source.$heldNote", held)
    }

    private fun baseWarning(events: List<Event>, input: SourceItem): String? = when {
        events.isEmpty() ->
            "No events found in \"${input.title}\". " +
            "This is common with formal institutional syllabi — they contain policies " +
            "(attendance, academic honesty, ADA, etc.) but not the actual course schedule. " +
            "Look for a separate weekly schedule or course calendar document from your professor. " +
            "If your syllabus uses week numbers like \"Due Week 4\", it also needs a table " +
            "mapping weeks to dates (e.g. \"Week 1: Aug 24–28\") somewhere in the same document."
        events.size < 5 && input.category == SourceCategory.OTHER ->
            "Only ${events.size} event(s) found in \"${input.title}\". " +
            "This document appears to be an institutional syllabus with policies rather than a " +
            "course schedule. If you are looking for assignment deadlines, check whether your " +
            "professor also provides a separate weekly schedule or course calendar — that document " +
            "will contain the due dates."
        else -> null
    }

    /** Tells the student when the semester window dropped events, so a wrong window is visible, not silent. */
    internal fun outsideWindowNote(outcome: ExtractionOutcome): String? {
        val window = outcome.window
        if (outcome.outsideWindow == 0 || window == null) return null
        val n = outcome.outsideWindow
        return "$n event${if (n == 1) "" else "s"} fall${if (n == 1) "s" else ""} outside your semester window " +
            "(${window.first} to ${window.second}) and ${if (n == 1) "was" else "were"} not added. " +
            "If that is the wrong term, change the window in Settings and add the source again."
    }
}
