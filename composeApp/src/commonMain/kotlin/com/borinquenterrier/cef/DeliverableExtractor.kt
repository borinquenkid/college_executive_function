package com.borinquenterrier.cef

data class ExtractionResult(val events: List<Event>, val warning: String?, val statusMessage: String)

internal class DeliverableExtractor(
    private val generationService: EventGenerationService,
    private val onProgress: (String) -> Unit = {}
) : AgentAction<SourceItem, ExtractionResult> {

    override suspend fun run(input: SourceItem, calendarId: String): ExtractionResult {
        val events = generationService.extractDeliverables(input) { message -> onProgress(message) }
        val warning = when {
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
        return ExtractionResult(events, warning, "${events.size} deadlines and exams found from entire source.")
    }
}
