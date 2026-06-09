package com.borinquenterrier.cef

/**
 * Orchestrates the complete source processing pipeline for the harness:
 * context analysis → deliverable extraction → study plan generation.
 */
class HarnessSourceProcessor(
    private val ingestionAgent: IngestionAgent,
    private val eventAgent: EventAgent,
    private val contextAgent: ContextAgent,
    private val logger: Logger,
    private val bugReporter: BugReporter? = null
) {
    private val tag = "HarnessSourceProcessor"

    /**
     * Processes a source sequentially through all analysis stages.
     * Step A: Context analysis (grading scales, policies)
     * Step B: Extract deliverables and push to calendar
     * Step C: Generate proactive study plans and push to calendar
     */
    suspend fun processSource(source: SourceItem) {
        try {
            logger.d(tag, "Extracting context analysis for: ${source.title}")
            contextAgent.analyzeSource(source)

            logger.d(tag, "Extracting deliverables for: ${source.title}")
            eventAgent.extractDeliverables(source)
            eventAgent.pushToCalendar()

            logger.d(tag, "Generating study plan for: ${source.title}")
            eventAgent.generateStudyPlan(source)
            eventAgent.pushToCalendar()
        } catch (e: Exception) {
            logger.e(tag, "Error processing source: ${source.title}", e)
            bugReporter?.reportError(e, "HarnessSourceProcessor processing: ${source.title}")
            throw e
        }
    }

    suspend fun processLocalFiles(files: List<String>, statusCallback: (String) -> Unit) {
        for (localFile in files) {
            statusCallback("Processing local file: ${localFile.substringAfterLast("/")}")
            try {
                logger.d(tag, "Processing new local file: $localFile")
                val source = ingestionAgent.addLocalFile(localFile)
                processSource(source)
            } catch (e: Exception) {
                logger.e(tag, "Error processing local file: $localFile", e)
                bugReporter?.reportError(e, "HarnessSourceProcessor local file: $localFile")
            }
        }
    }

    suspend fun processDriveFiles(files: List<DriveFile>, statusCallback: (String) -> Unit) {
        for (driveFile in files) {
            statusCallback("Processing GDrive file: ${driveFile.name}")
            try {
                logger.d(tag, "Processing new GDrive file: ${driveFile.name}")
                val source = ingestionAgent.addDriveFile(driveFile)
                processSource(source)
            } catch (e: Exception) {
                logger.e(tag, "Error processing GDrive file: ${driveFile.name}", e)
                bugReporter?.reportError(e, "HarnessSourceProcessor GDrive file: ${driveFile.name}")
            }
        }
    }
}
