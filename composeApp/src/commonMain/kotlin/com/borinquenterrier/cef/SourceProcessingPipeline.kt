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
    private val bugReporter: BugReporter? = null
) {
    private val tag = "SourceProcessingPipeline"

    suspend fun processSource(source: SourceItem) {
        try {
            // Keep the status honest through the context phase (an LLM call): without this the
            // Studio status would show a stale message from the previous step while it ran.
            eventAgent.updateStatus("Reading ${source.title}…")
            logger.d(tag, "Analyzing context for: ${source.title}")
            contextAgent.analyzeSource(source)

            logger.d(tag, "Extracting deliverables for: ${source.title}")
            eventAgent.extractDeliverables(source)
            eventAgent.pushToCalendar()
        } catch (e: Exception) {
            logger.e(tag, "Error processing source: ${source.title}", e)
            bugReporter?.reportError(e, "SourceProcessingPipeline: ${source.title}")
            throw e
        }
    }
}
