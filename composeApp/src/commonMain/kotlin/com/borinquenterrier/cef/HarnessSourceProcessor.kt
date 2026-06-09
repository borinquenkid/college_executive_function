package com.borinquenterrier.cef

/**
 * Lightweight facade orchestrating the complete source processing pipeline for the harness.
 * Delegates to specialized services:
 * - SourceProcessingPipeline: Core 3-step analysis (context → deliverables → study plan)
 * - LocalFileProcessor: Local file ingestion and processing
 * - DriveFileProcessor: GDrive file ingestion and processing
 */
class HarnessSourceProcessor(
    private val pipeline: SourceProcessingPipeline,
    private val localFileProcessor: LocalFileProcessor,
    private val driveFileProcessor: DriveFileProcessor,
    private val logger: Logger
) {
    private val tag = "HarnessSourceProcessor"

    suspend fun processSource(source: SourceItem) {
        logger.d(tag, "Processing source: ${source.title}")
        pipeline.processSource(source)
    }

    suspend fun processLocalFiles(files: List<String>, statusCallback: (String) -> Unit) {
        logger.d(tag, "Processing ${files.size} local file(s)")
        localFileProcessor.processLocalFiles(files, statusCallback)
    }

    suspend fun processDriveFiles(files: List<DriveFile>, statusCallback: (String) -> Unit) {
        logger.d(tag, "Processing ${files.size} GDrive file(s)")
        driveFileProcessor.processDriveFiles(files, statusCallback)
    }
}
