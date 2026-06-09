package com.borinquenterrier.cef

/**
 * Handles the processing of local files through the source pipeline.
 * Orchestrates ingestion and sequential analysis of local file paths.
 */
class LocalFileProcessor(
    private val ingestionAgent: IngestionAgent,
    private val pipeline: SourceProcessingPipeline,
    private val logger: Logger,
    private val bugReporter: BugReporter? = null
) {
    private val tag = "LocalFileProcessor"

    suspend fun processLocalFiles(files: List<String>, statusCallback: (String) -> Unit) {
        for (localFile in files) {
            statusCallback("Processing local file: ${localFile.substringAfterLast("/")}")
            try {
                logger.d(tag, "Processing new local file: $localFile")
                val source = ingestionAgent.addLocalFile(localFile)
                pipeline.processSource(source)
            } catch (e: Exception) {
                logger.e(tag, "Error processing local file: $localFile", e)
                bugReporter?.reportError(e, "LocalFileProcessor: $localFile")
            }
        }
    }
}
