package com.borinquenterrier.cef

/**
 * Encapsulates the core 3-step source processing pipeline:
 * Context analysis → Deliverable extraction → Study plan generation.
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
            logger.d(tag, "Extracting context analysis for: ${source.title}")
            contextAgent.analyzeSource(source)

            logger.d(tag, "Extracting deliverables for: ${source.title}")
            eventAgent.extractDeliverables(source)
            eventAgent.pushToCalendar()
            eventAgent.autoDecomposeDeliverables()

            logger.d(tag, "Generating study plan for: ${source.title}")
            eventAgent.generateStudyPlan(source)
            eventAgent.pushToCalendar()
        } catch (e: Exception) {
            logger.e(tag, "Error processing source: ${source.title}", e)
            bugReporter?.reportError(e, "SourceProcessingPipeline: ${source.title}")
            throw e
        }
    }
}
