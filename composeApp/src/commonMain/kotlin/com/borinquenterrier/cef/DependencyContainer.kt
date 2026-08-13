package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.DriverFactory
import com.borinquenterrier.cef.db.createDatabase
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlinx.serialization.json.Json

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
    val pdfReader: PdfReader,
    val clock: Clock = Clock.System,
    // Tests inject an in-memory AppDatabase here so they never touch the real cef.db on disk.
    // Null (production) → the database is opened from the app's data directory via driverFactory.
    private val injectedDatabase: AppDatabase? = null
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
            // Without this, a hung connection (captive portal, stalled TLS handshake) never
            // throws — the coroutine just suspends forever, so "Connecting…"/sync-in-progress
            // UI states can hang indefinitely with no error ever surfacing to catch.
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    val appEnv: AppEnv = AppEnv()
    val database: AppDatabase by lazy { injectedDatabase ?: createDatabase(driverFactory) }
    val tokenRepository by lazy { GoogleTokenRepository(settings) }
    val authService by lazy { GoogleAuthService(settings, appEnv) }
    val tokenService by lazy {
        GoogleTokenService(tokenRepository, authService) { message ->
            googleAccountFlow.reportAuthError(message)
        }
    }
    val localRepository by lazy { SqlDelightLocalCalendarRepository(database, settings) }
    val preferencesRepository by lazy { PreferencesRepository(settings) }
    val preferencesFlow: StateFlow<StudyPreferences> by lazy { preferencesRepository.flow.asStateFlow() }
    val userPreferenceMemoryRepository by lazy { SqlDelightUserPreferenceMemoryRepository(database) }
    val syncService by lazy { GoogleCalendarSyncService(httpClient, tokenService) }
    val calendarIdResolver by lazy { CalendarIdResolver(syncService, preferencesRepository) }
    val conflictDetector by lazy { EventConflictDetector() }
    val remoteRepository by lazy {
        GoogleRemoteCalendarRepository(
            syncService,
            calendarIdResolver,
            conflictDetector
        )
    }
    // Cross-term memory (ADR 0004 / ROADMAP Phase 13, XM-1..5) — read side is contextAgent below,
    // write side is calendarAgent's post-sync term-boundary check.
    val termProfileRepository by lazy { TermProfileRepository(database) }

    val calendarAgent by lazy {
        CalendarAgent(
            localRepository,
            remoteRepository,
            logger,
            userPreferenceMemoryRepository,
            preferencesRepository,
            sourceRepository,
            termProfileRepository = termProfileRepository
        )
    }

    val googleAccountFlow: GoogleAccountFlow by lazy {
        GoogleAccountFlow(authService, tokenRepository, syncService)
    }

    val webReader by lazy { WebSourceReader() }

    init {
        globalScope.launch {
            googleAccountFlow.checkConnectionOnStartup()
        }
    }

    /**
     * Cancels the startup-check coroutine and anything else launched on [globalScope], and
     * suspends until it has actually finished — a bare `cancel()` only marks the job cancelling;
     * a blocking JDBC call already in flight keeps running and can still throw uncaught after the
     * caller has moved on (e.g. deleted the temp dir backing it in a test).
     */
    suspend fun close() {
        globalScope.coroutineContext[Job]?.cancelAndJoin()
    }

    /**
     * Runs [block] detached from whatever request/caller triggered it, on the same
     * application-scoped [globalScope] used elsewhere in this class (e.g. the startup check
     * above) — not a new scope. Used by the web ingestion path (ADR 0012) to launch
     * [SourceProcessingPipeline.processSource] without holding the HTTP response open for it.
     */
    fun launchInBackground(block: suspend CoroutineScope.() -> Unit): Job = globalScope.launch(block = block)

    val telemetryManager by lazy { TelemetryManager(settings) }
    val bugReporter by lazy {
        BugReporter(
            httpClient,
            preferencesRepository,
            telemetryManager,
            logger
        )
    }

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
    val chatRepository by lazy { SqlDelightChatRepository(database) }
    val analysisCacheRepository by lazy { SqlDelightAnalysisCacheRepository(database) }
    val ingestionAgent by lazy {
        IngestionAgent(
            fileReader,
            docxReader,
            pdfReader,
            webReader,
            aiService,
            sourceRepository
        )
    }
    val termNormalizer by lazy { TermNormalizer() }
    val documentFrequencyCalculator by lazy { DocumentFrequencyCalculator() }
    val tfIdfScorer by lazy { TFIDFScorer() }
    val fragmentRanker by lazy {
        FragmentRanker(
            termNormalizer,
            documentFrequencyCalculator,
            tfIdfScorer
        )
    }
    val contextBuilder by lazy { SourceContextBuilder() }
    val contextAgent by lazy {
        ContextAgent(
            aiService,
            sourceRepository,
            fragmentRanker,
            contextBuilder,
            logger,
            chatRepository,
            termProfileRepository = termProfileRepository,
            // Deadline-safety channel (tasks/plan.md T4): chat's calendar digest reads the synced
            // local calendar — the ground truth the student actually acts on.
            eventsProvider = { localRepository.getAllEvents() }
        )
    }
    val syllabusAuditor by lazy { SyllabusAuditor(aiService, logger) }

    val taskDecompositionService by lazy {
        TaskDecompositionService(aiService, calendarAgent, sourceRepository, logger)
    }

    val sharedEventGenerationService by lazy {
        EventGenerationService(
            aiService,
            NormalizationService(),
            syllabusAuditor,
            preferencesRepository,
            userPreferenceMemoryRepository
        )
    }

    val eventAgent by lazy {
        EventAgent(
            aiService,
            calendarAgent,
            database,
            NormalizationService(),
            preferencesRepository,
            logger,
            userPreferenceMemoryRepository,
            clock,
            analysisCacheRepository,
            sourceRepository
        )
    }

    val pollScheduler by lazy { PollScheduler(settings, logger) }

    val preferenceSerializer by lazy { PreferenceSerializer(logger) }

    val localDirectoryPreferences by lazy {
        LocalDirectoryPreferences(
            settings,
            preferenceSerializer,
            logger
        )
    }

    val directoryPreferencesManager by lazy {
        DirectoryPreferencesManager(localDirectoryPreferences)
    }

    val localFileScanner by lazy {
        LocalFileScanner(
            fileReader,
            directoryPreferencesManager,
            logger
        )
    }

    val sourceScanner by lazy {
        SourceScanner(
            directoryPreferencesManager,
            localFileScanner
        )
    }

    val sourceProcessingPipeline by lazy {
        SourceProcessingPipeline(
            ingestionAgent,
            eventAgent,
            contextAgent,
            logger,
            bugReporter,
            sourceRepository
        )
    }

    val localFileProcessor by lazy {
        LocalFileProcessor(
            ingestionAgent,
            sourceProcessingPipeline,
            logger,
            bugReporter
        )
    }

    val harnessSourceProcessor by lazy {
        HarnessSourceProcessor(
            sourceProcessingPipeline,
            localFileProcessor,
            logger
        )
    }

    val sourceLoader by lazy { SourceLoader(sourceRepository, logger, globalScope) }

    val sourceAdder by lazy {
        SourceAdder(
            aiService,
            sharedEventGenerationService,
            contextAgent,
            logger,
            globalScope,
            analysisCacheRepository,
            sourceRepository,
            onEventsAdded = { newEvents ->
                val existing = eventAgent.lastGeneratedEvents.value
                eventAgent.setGeneratedEvents(EventDeduplicator.dedup(existing + newEvents))
            },
            onError = { error -> eventAgent.reportError(error) },
            preferencesRepository = preferencesRepository
        )
    }

    val sourceDeleter by lazy {
        SourceDeleter(
            sourceRepository,
            calendarAgent,
            logger,
            globalScope
        )
    }

    val sourceSelector by lazy { SourceSelector() }

    val sourceManager by lazy {
        SourceManager(
            sourceLoader,
            sourceAdder,
            sourceDeleter,
            sourceSelector,
            globalScope
        )
    }

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
            userPreferenceMemoryRepository,
            logger,
            bugReporter
        )
    }

    val appController by lazy { AppController(this) }
}
