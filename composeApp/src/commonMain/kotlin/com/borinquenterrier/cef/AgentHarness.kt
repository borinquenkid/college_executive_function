package com.borinquenterrier.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Lightweight facade orchestrating the autonomous background polling harness.
 * Delegates to PollScheduler, SourceScanner, and HarnessSourceProcessor.
 */
class AgentHarness(
    private val ingestionAgent: IngestionAgent,
    private val eventAgent: EventAgent,
    private val contextAgent: ContextAgent,
    private val calendarAgent: CalendarAgent,
    private val sourceRepository: SourceRepository,
    private val pollScheduler: PollScheduler,
    private val sourceScanner: SourceScanner,
    private val sourceProcessor: HarnessSourceProcessor,
    private val userPreferenceMemoryRepository: UserPreferenceMemoryRepository,
    private val logger: Logger,
    private val bugReporter: BugReporter? = null
) {
    private val tag = "AgentHarness"

    /** Nearest-due deliverables decomposed per poll — keeps a big backlog from burning through
     *  a large chunk of the day's AI quota in one background run (see AutoDecomposer). */
    private val autoDecomposeCapPerPoll = 2

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun getLastPollTime(): Long = pollScheduler.getLastPollTime()

    fun setLastPollTime(timeMs: Long) = pollScheduler.setLastPollTime(timeMs)

    fun getWatchedLocalDirectories(): List<String> = sourceScanner.getWatchedLocalDirectories()

    fun setWatchedLocalDirectories(dirs: List<String>) =
        sourceScanner.setWatchedLocalDirectories(dirs)

    suspend fun runHarness(force: Boolean = false) {
        if (!pollScheduler.shouldPoll(force)) return

        if (_isBusy.value) {
            logger.d(tag, "AgentHarness is already running. Skipping.")
            return
        }

        _isBusy.value = true
        _status.value = "Starting file polling and calendar sync..."
        logger.d(tag, "Starting background polling loop...")

        try {
            val existingSources = sourceRepository.getAllSources()
            val existingUris = existingSources.mapNotNull { it.originUri }.toSet()

            val newLocalFiles = sourceScanner.scanNewLocalFiles(existingUris)
            logger.d(tag, "Found ${newLocalFiles.size} new local files.")

            eventAgent.loadIncompleteEvents()
            sourceProcessor.processLocalFiles(newLocalFiles) { status -> _status.value = status }

            _status.value = "Synchronizing calendar..."
            logger.d(tag, "Running calendar synchronization...")
            calendarAgent.synchronize("default")

            _status.value = "Breaking down upcoming deliverables..."
            logger.d(tag, "Auto-decomposing up to $autoDecomposeCapPerPoll nearest-due deliverable(s)...")
            eventAgent.autoDecomposeDeliverables(calendarId = "default", maxDeliverables = autoDecomposeCapPerPoll)

            val retentionCutoff = Clock.System.now().toEpochMilliseconds() -
                UserPreferenceMemoryRepository.OVERRIDE_LOG_RETENTION_MS
            userPreferenceMemoryRepository.pruneOldLogs(retentionCutoff)

            pollScheduler.setLastPollTime(Clock.System.now().toEpochMilliseconds())
            _status.value = "Completed successfully."
            logger.d(tag, "Harness run completed successfully.")
        } catch (e: Exception) {
            logger.e(tag, "Harness execution failed", e)
            _status.value = "Failed: ${e.message}"
            bugReporter?.reportError(e, "AgentHarness.runHarness main loop")
        } finally {
            _isBusy.value = false
        }
    }
}
