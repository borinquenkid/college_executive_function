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
    val modelBasePath: String
) {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    val database: AppDatabase = createDatabase(driverFactory)
    val modelManager = ModelManager(httpClient, modelBasePath, logger)
    val tokenRepository = GoogleTokenRepository(settings)
    val authService = GoogleAuthService(settings)
    
    val localRepository = SqlDelightLocalCalendarRepository(database)
    val syncService = GoogleCalendarSyncService(httpClient)
    val remoteRepository = GoogleRemoteCalendarRepository(syncService, tokenRepository, authService)
    val unifiedRepository = UnifiedCalendarRepository(localRepository, remoteRepository)

    val driveService = GoogleDriveService(httpClient, tokenRepository, authService)
    val aiService = AIService(settings, logger, database)
    
    val webReader = WebSourceReader()
    val fileReader = LocalFileReader()
    val docxReader = DocxReader()
    val pdfReader = PdfReader()

    val googleAccountFlow = GoogleAccountFlow(authService, tokenRepository, driveService)
    val sourceFlow = SourceFlow(fileReader, docxReader, pdfReader, webReader, driveService, aiService)
    val studioFlow = StudioFlow(aiService, unifiedRepository, database, KeywordEventExtractor(), logger)
}
