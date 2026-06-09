package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.createDatabase
import com.borinquenterrier.cef.db.DriverFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * A central container for all application services and repositories.
 * This enables "Headless" execution by allowing the logic to exist 
 * independently of the Compose UI.
 */
class DependencyContainer(
    val settings: Settings,
    val logger: Logger,
    val driverFactory: DriverFactory,
    val modelBasePath: String,
    val fileReader: LocalFileReader,
    val docxReader: DocxReader,
    val pdfReader: PdfReader
) {
    private val globalScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
    }

    val database: AppDatabase by lazy { createDatabase(driverFactory) }
    val modelManager by lazy { ModelManager(httpClient, modelBasePath, logger) }
    val tokenRepository by lazy { GoogleTokenRepository(settings) }
    val authService by lazy { GoogleAuthService(settings) }
    val localRepository by lazy { SqlDelightLocalCalendarRepository(database, settings) }
    val preferencesRepository by lazy { PreferencesRepository(settings) }
    val userPreferenceMemoryRepository by lazy { SqlDelightUserPreferenceMemoryRepository(database) }
    val syncService by lazy { GoogleCalendarSyncService(httpClient, tokenRepository, authService) }
    val calendarIdResolver by lazy { CalendarIdResolver(syncService, preferencesRepository) }
    val conflictDetector by lazy { EventConflictDetector() }
    val eventRangeFilter by lazy { EventRangeFilter() }
    val remoteRepository by lazy { GoogleRemoteCalendarRepository(syncService, preferencesRepository, calendarIdResolver, conflictDetector, eventRangeFilter) }
    val calendarAgent by lazy { CalendarAgent(localRepository, remoteRepository, logger, userPreferenceMemoryRepository, preferencesRepository) }

    val googleAccountFlow by lazy { GoogleAccountFlow(authService, tokenRepository) }

    val driveService: GoogleDriveService by lazy { 
        GoogleDriveService(httpClient, tokenRepository, authService, { googleAccountFlow.reportAuthError(it) }) 
    }

    val webReader by lazy { WebSourceReader() }

    init {
        googleAccountFlow.driveService = driveService
    }
    val telemetryManager by lazy { TelemetryManager(settings) }
    val bugReporter by lazy { BugReporter(httpClient, preferencesRepository, telemetryManager, logger) }

    val aiService: AIService by lazy {
        GroundingGuardAIService(
            CriticActorAIService(
                RecursiveDecompositionAIService(RealAIService(settings, logger, database)),
                logger,
                telemetryManager
            ),
            logger
        )
    }
    val sourceRepository by lazy { SqlDelightSourceRepository(database) }
    val ingestionAgent by lazy { IngestionAgent(fileReader, docxReader, pdfReader, webReader, driveService, aiService, sourceRepository) }
    val fragmentRanker by lazy { FragmentRanker() }
    val contextBuilder by lazy { SourceContextBuilder() }
    val contextAgent by lazy { ContextAgent(aiService, sourceRepository, fragmentRanker, contextBuilder, logger) }
    val eventAgent by lazy { EventAgent(aiService, calendarAgent, database, NormalizationService(), preferencesRepository, logger, userPreferenceMemoryRepository) }

    val pollScheduler by lazy { PollScheduler(settings, logger) }

    val preferenceSerializer by lazy { PreferenceSerializer(logger) }

    val localDirectoryPreferences by lazy { LocalDirectoryPreferences(settings, preferenceSerializer, logger) }

    val driveDirectoryPreferences by lazy { DriveDirectoryPreferences(settings, preferenceSerializer, logger) }

    val directoryPreferencesManager by lazy { DirectoryPreferencesManager(localDirectoryPreferences, driveDirectoryPreferences) }

    val localFileScanner by lazy { LocalFileScanner(fileReader, directoryPreferencesManager, logger) }

    val driveFileScanner by lazy { DriveFileScanner(driveService, tokenRepository, directoryPreferencesManager, logger) }

    val sourceScanner by lazy { SourceScanner(directoryPreferencesManager, localFileScanner, driveFileScanner) }

    val sourceProcessingPipeline by lazy { SourceProcessingPipeline(ingestionAgent, eventAgent, contextAgent, logger, bugReporter) }

    val localFileProcessor by lazy { LocalFileProcessor(ingestionAgent, sourceProcessingPipeline, logger, bugReporter) }

    val driveFileProcessor by lazy { DriveFileProcessor(ingestionAgent, sourceProcessingPipeline, logger, bugReporter) }

    val harnessSourceProcessor by lazy { HarnessSourceProcessor(sourceProcessingPipeline, localFileProcessor, driveFileProcessor, logger) }

    val sourceLoader by lazy { SourceLoader(sourceRepository, logger, globalScope) }

    val sourceAdder by lazy { SourceAdder(aiService, contextAgent, logger, globalScope, { events -> /* events will be handled by EventAgent */ }) }

    val sourceDeleter by lazy { SourceDeleter(sourceRepository, localRepository, calendarAgent, logger, globalScope) }

    val sourceSelector by lazy { SourceSelector() }

    val sourceManager by lazy { SourceManager(sourceLoader, sourceAdder, sourceDeleter, sourceSelector, globalScope) }

    val agentHarness by lazy {
       AgentHarness(
           ingestionAgent,
           eventAgent,
           contextAgent,
           calendarAgent,
           sourceRepository,
           pollScheduler,
           sourceScanner,
           harnessSourceProcessor,
           logger,
           bugReporter
       )
    }

    val googleAuthManager by lazy { GoogleAuthManager(authService, tokenRepository, logger) }

    val calendarSyncManager by lazy { CalendarSyncManager(calendarAgent, logger) }

    val appController by lazy { AppController(this) }
}
