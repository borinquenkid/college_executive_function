package com.borinquenterrier.cef

/**
 * Handles the processing of Google Drive files through the source pipeline.
 * Orchestrates ingestion and sequential analysis of GDrive files.
 */
class DriveFileProcessor(
    private val ingestionAgent: IngestionAgent,
    private val pipeline: SourceProcessingPipeline,
    private val logger: Logger,
    private val bugReporter: BugReporter? = null
) {
    private val tag = "DriveFileProcessor"

    suspend fun processDriveFiles(files: List<DriveFile>, statusCallback: (String) -> Unit) {
        for (driveFile in files) {
            statusCallback("Processing GDrive file: ${driveFile.name}")
            try {
                logger.d(tag, "Processing new GDrive file: ${driveFile.name}")
                val source = ingestionAgent.addDriveFile(driveFile)
                pipeline.processSource(source)
            } catch (e: Exception) {
                logger.e(tag, "Error processing GDrive file: ${driveFile.name}", e)
                bugReporter?.reportError(e, "DriveFileProcessor: ${driveFile.name}")
            }
        }
    }
}
