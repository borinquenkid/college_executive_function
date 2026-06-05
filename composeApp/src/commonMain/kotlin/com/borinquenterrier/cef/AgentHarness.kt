package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class AgentHarness(
    private val ingestionAgent: IngestionAgent,
    private val eventAgent: EventAgent,
    private val contextAgent: ContextAgent,
    private val calendarAgent: CalendarAgent,
    private val driveService: GoogleDriveService,
    private val tokenRepository: GoogleTokenRepository,
    private val fileReader: LocalFileReader,
    private val sourceRepository: SourceRepository,
    private val settings: Settings,
    private val logger: Logger,
    private val bugReporter: BugReporter? = null
) {
    private val tag = "AgentHarness"
    private val lastPollTimeKey = "cef_harness_last_poll_time"

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun getLastPollTime(): Long {
        return settings.getLong(lastPollTimeKey, 0L)
    }

    fun setLastPollTime(timeMs: Long) {
        settings.putLong(lastPollTimeKey, timeMs)
    }

    fun getWatchedLocalDirectories(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_LOCAL_DIRECTORIES", "")
        if (jsonString.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setWatchedLocalDirectories(dirs: List<String>) {
        settings.putString("CEF_WATCHED_LOCAL_DIRECTORIES", Json.encodeToString(dirs))
    }

    fun getWatchedGDriveFolders(): List<String> {
        val jsonString = settings.getString("CEF_WATCHED_GDRIVE_FOLDERS", "")
        if (jsonString.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setWatchedGDriveFolders(folders: List<String>) {
        settings.putString("CEF_WATCHED_GDRIVE_FOLDERS", Json.encodeToString(folders))
    }

    suspend fun runHarness(force: Boolean = false) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force) {
            val lastPoll = getLastPollTime()
            val twentyFourHoursMs = 24 * 60 * 60 * 1000L
            if (now - lastPoll < twentyFourHoursMs) {
                logger.d(tag, "24 hours have not passed since last poll. Skipping.")
                return
            }
        }

        if (_isBusy.value) {
            logger.d(tag, "AgentHarness is already running. Skipping.")
            return
        }

        _isBusy.value = true
        _status.value = "Starting file polling and calendar sync..."
        logger.d(tag, "Starting background polling loop...")

        try {
            // Get existing sources' origin URIs to avoid duplicates
            val existingSources = sourceRepository.getAllSources()
            val existingUris = existingSources.mapNotNull { it.originUri }.toSet()

            val newLocalFiles = mutableListOf<String>()
            val newDriveFiles = mutableListOf<DriveFile>()

            coroutineScope {
                // 1. Scan Local watched directories concurrently
                val localDirs = getWatchedLocalDirectories()
                val localDeferreds = localDirs.map { dir ->
                    async {
                        try {
                            fileReader.listFiles(dir)
                        } catch (e: Exception) {
                            logger.e(tag, "Failed to list local files in directory: $dir", e)
                            emptyList<String>()
                        }
                    }
                }

                // 2. Scan GDrive watched folders concurrently (if authenticated)
                val driveDeferreds = if (tokenRepository.hasTokens()) {
                    val driveFolders = getWatchedGDriveFolders()
                    driveFolders.map { folderId ->
                        async {
                            try {
                                val query = "'$folderId' in parents and (mimeType = 'application/vnd.google-apps.document' " +
                                        "or mimeType = 'application/pdf' " +
                                        "or mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' " +
                                        "or mimeType = 'text/plain' " +
                                        "or name contains '.ics')"
                                driveService.listFiles(query)
                            } catch (e: Exception) {
                                logger.e(tag, "Failed to list files for drive folder: $folderId", e)
                                emptyList<DriveFile>()
                            }
                        }
                    }
                } else {
                    logger.d(tag, "Skipping GDrive scanning as auth is not available/configured.")
                    emptyList()
                }

                // Await all local directories results
                val localResults = localDeferreds.map { it.await() }
                for (files in localResults) {
                    for (file in files) {
                        if (!existingUris.contains(file) && !newLocalFiles.contains(file)) {
                            newLocalFiles.add(file)
                        }
                    }
                }

                // Await all GDrive folders results
                val driveResults = driveDeferreds.map { it.await() }
                for (files in driveResults) {
                    for (file in files) {
                        val uri = "google_drive://${file.id}"
                        if (!existingUris.contains(uri) && newDriveFiles.none { it.id == file.id }) {
                            newDriveFiles.add(file)
                        }
                    }
                }
            }

            logger.d(tag, "Found ${newLocalFiles.size} new local files and ${newDriveFiles.size} new GDrive files.")

            // Immediately after the files have been polled: load incomplete events for check-in
            eventAgent.loadIncompleteEvents()

            // 3. Process new files sequentially one by one: Ingestion -> Deliverables -> Context analysis -> Study Plan
            for (localFile in newLocalFiles) {
                _status.value = "Processing local file: ${localFile.substringAfterLast("/")}"
                try {
                    logger.d(tag, "Processing new local file: $localFile")
                    val source = ingestionAgent.addLocalFile(localFile)
                    processSourceSequentially(source)
                } catch (e: Exception) {
                    logger.e(tag, "Error processing local file: $localFile", e)
                    bugReporter?.reportError(e, "AgentHarness processing local file: $localFile")
                }
            }

            for (driveFile in newDriveFiles) {
                _status.value = "Processing GDrive file: ${driveFile.name}"
                try {
                    logger.d(tag, "Processing new GDrive file: ${driveFile.name}")
                    val source = ingestionAgent.addDriveFile(driveFile)
                    processSourceSequentially(source)
                } catch (e: Exception) {
                    logger.e(tag, "Error processing GDrive file: ${driveFile.name}", e)
                    bugReporter?.reportError(e, "AgentHarness processing GDrive file: ${driveFile.name}")
                }
            }

            // 4. Run two-way calendar sync at the end
            _status.value = "Synchronizing calendar..."
            logger.d(tag, "Running calendar synchronization...")
            calendarAgent.synchronize("default")

            // 5. Update last poll time
            setLastPollTime(now)
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

    private suspend fun processSourceSequentially(source: SourceItem) {
        // Step A: Deep semantic analysis (ContextAgent) to extract grading scales/rules
        logger.d(tag, "Extracting context analysis for: ${source.title}")
        contextAgent.analyzeSource(source)

        // Step B: Extract standard deliverables & push to calendar
        logger.d(tag, "Extracting deliverables for: ${source.title}")
        eventAgent.extractDeliverables(source)
        eventAgent.pushToCalendar()

        // Step C: Generate proactive study plans & push to calendar
        logger.d(tag, "Generating study plan for: ${source.title}")
        eventAgent.generateStudyPlan(source)
        eventAgent.pushToCalendar()
    }
}
