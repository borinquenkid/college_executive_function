package com.borinquenterrier.cef

/**
 * Encapsulates the auto-run ingestion pipeline: context analysis → deliverable extraction → push.
 *
 * Study-plan generation and task decomposition are deliberately NOT auto-run here — each is a burst
 * of LLM calls (a study plan generates dozens of study blocks; decomposition runs a critique loop
 * per deliverable) that blew past Gemini rate limits and stalled ingestion for minutes. Both stay
 * on-demand as Studio actions the student triggers per source.
 */
class SourceProcessingPipeline(
    private val ingestionAgent: IngestionAgent,
    private val eventAgent: EventAgent,
    private val contextAgent: ContextAgent,
    private val logger: Logger,
    private val bugReporter: BugReporter? = null,
    // Optional (ADR 0012): lets processSource record DONE/FAILED against the source's lifecycle
    // status for the web async-ingestion path. Null in tests/call sites that don't care.
    private val sourceRepository: SourceRepository? = null
) {
    private val tag = "SourceProcessingPipeline"

    suspend fun processSource(source: SourceItem) {
        try {
            // Keep the status honest through the context phase (an LLM call): without this the
            // Studio status would show a stale message from the previous step while it ran.
            eventAgent.updateStatus("Reading ${source.title}…")
            sourceRepository?.updateSourceStatus(source.id, SourceStatus.ANALYZING_CONTEXT)
            logger.d(tag, "Analyzing context for: ${source.title}")
            val contextOk = contextAgent.analyzeSource(source)

            sourceRepository?.updateSourceStatus(source.id, SourceStatus.EXTRACTING_DELIVERABLES)
            logger.d(tag, "Extracting deliverables for: ${source.title}")
            val extractOk = eventAgent.extractDeliverables(source)

            // A chunk's AI call failing internally (contextOk/extractOk false) is not itself a
            // pipeline failure — product decision: if every chunk was at least attempted, this is a
            // success even if nothing useful was found. It's still worth tracing explicitly (both
            // calls already export an errored OTEL span via AppTracer when this happens, but that's
            // easy to miss without a plain log line naming which source and step failed).
            if (!contextOk || !extractOk) {
                logger.e(
                    tag,
                    "Chunk failure while processing ${source.title} (contextOk=$contextOk, " +
                        "extractOk=$extractOk) — continuing to DONE; see the AI service's own error above"
                )
            }

            // Conflict resolution and the actual calendar write both happen inside this one call
            // with no observable boundary between them (see SourceStatus's kdoc) — one phase covers
            // the whole step.
            sourceRepository?.updateSourceStatus(source.id, SourceStatus.RESOLVING_CONFLICTS)
            eventAgent.pushToCalendar()
            sourceRepository?.updateSourceStatus(source.id, SourceStatus.DONE)
        } catch (e: Exception) {
            logger.e(tag, "Error processing source: ${source.title}", e)
            bugReporter?.reportError(e, "SourceProcessingPipeline: ${source.title}")
            sourceRepository?.updateSourceStatus(source.id, SourceStatus.FAILED)
            throw e
        }
    }
}
