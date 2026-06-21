package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking

class IcsToGoogleIntegrationTest : FunSpec({

    test("Should clear calendar, parse sample.ics and push events to Google Repository").config(
        enabled = false
    ) {
        // 1. Load sample.ics from resources
        val icsStream = object {}.javaClass.classLoader.getResourceAsStream("sample.ics")
        if (icsStream == null) {
            throw AssertionError("Could not find sample.ics in test resources")
        }
        val icsContent = icsStream.bufferedReader().use { it.readText() }
        icsContent.isNotEmpty() shouldBe true

        // 2. Setup Google API
        val settings = com.russhwolf.settings.MapSettings()

        val authService = GoogleAuthService(settings, AppEnv())
        val (accessToken, refreshToken) = runBlocking {
            println("Attempting to log in with Google. Please follow the browser prompts.")
            authService.login()
        }
        val tokenRepo = GoogleTokenRepository(settings)
        tokenRepo.saveTokens(accessToken, refreshToken)

        val httpClient = HttpClient(io.ktor.client.engine.java.Java) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
        }


        val syncService = GoogleCalendarSyncService(httpClient, GoogleTokenService(tokenRepo, authService))
        val preferencesRepository = PreferencesRepository(settings)
        val idResolver = CalendarIdResolver(syncService, preferencesRepository)
        val conflictDetector = EventConflictDetector()
        val googleRepo = GoogleRemoteCalendarRepository(
            syncService,
            preferencesRepository,
            idResolver,
            conflictDetector,
        )
        val icsSource = IcsCalendarSource(icsContent)

        // 3. Setup AI for unified pipeline extraction
        // In a real integration test, we might use the real AIService if a model is present
        // For now, we'll use the existing icsSource.extractChunks() and an AI to map them
        val logger = Logger(settings)
        val aiService: AIService = RealAIService(settings, logger, null)

        // 4. Clear existing events (USE CAUTION IF RUNNING AGAINST REAL CALENDAR)
        println("WARNING: RUNNING AGAINST REAL GOOGLE CALENDAR. CLEARING EVENTS...")

        try {
            runBlocking {
                googleRepo.clearCalendar("primary")
            }
        } catch (e: GoogleApiException) {
            println("FAILED TO CLEAR CALENDAR: ${e.statusCode} - ${e.responseBody}")
            throw e
        }

        // 5. Extract via unified pipeline and Push
        val parts = runBlocking { icsSource.readSource() }
        parts.isNotEmpty() shouldBe true

        val allEvents = runBlocking {
            if (aiService.isConfigured()) {
                aiService.generateCalendarEvents(parts.take(5))
            } else {
                emptyList()
            }
        }

        var successCount = 0
        runBlocking {
            allEvents.forEach { event ->
                try {
                    googleRepo.saveEvent(event)
                    successCount++
                } catch (e: GoogleApiException) {
                    println("FAILED TO PUSH EVENT '${event.title}': ${e.statusCode} - ${e.responseBody}")
                }
            }
        }

        successCount shouldBe allEvents.size
    }
})
