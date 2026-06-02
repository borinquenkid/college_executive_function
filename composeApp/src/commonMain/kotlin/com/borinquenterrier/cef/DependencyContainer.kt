package com.borinquenterrier.cef

import com.russhwolf.settings.Settings
import com.borinquenterrier.cef.db.AppDatabase
import com.borinquenterrier.cef.db.createDatabase
import com.borinquenterrier.cef.db.DriverFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
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
    val pdfReader: PdfReader
) {
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
    val syncService by lazy { GoogleCalendarSyncService(httpClient) }
    val remoteRepository by lazy { GoogleRemoteCalendarRepository(syncService, tokenRepository, authService) }
    val calendarAgent by lazy { CalendarAgent(localRepository, remoteRepository) }

    val googleAccountFlow by lazy { GoogleAccountFlow(authService, tokenRepository) }

    val driveService: GoogleDriveService by lazy { 
        GoogleDriveService(httpClient, tokenRepository, authService, { googleAccountFlow.reportAuthError(it) }) 
    }

    val webReader by lazy { WebSourceReader() }

    init {
        googleAccountFlow.driveService = driveService
    }
    val aiService: AIService by lazy { 
        RecursiveDecompositionAIService(RealAIService(settings, logger, database)) 
    }
    val ingestionAgent by lazy { IngestionAgent(fileReader, docxReader, pdfReader, webReader, driveService, aiService, database) }
    val contextAgent by lazy { ContextAgent(aiService, database, logger) }
    val eventAgent by lazy { EventAgent(aiService, calendarAgent, database, NormalizationService(), logger) }

    val appController by lazy { AppController(this) }
}
