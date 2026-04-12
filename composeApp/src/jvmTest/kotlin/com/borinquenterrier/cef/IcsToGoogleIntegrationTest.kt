package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File

class IcsToGoogleIntegrationTest : FunSpec({

    test("Should clear calendar, parse sample.ics and push events to Google Repository") {
        // 1. Load sample.ics from resources
        val icsStream = object {}.javaClass.classLoader.getResourceAsStream("sample.ics")
        if (icsStream == null) {
            throw AssertionError("Could not find sample.ics in test resources")
        }
        val icsContent = icsStream.bufferedReader().use { it.readText() }
        icsContent.isNotEmpty() shouldBe true

        // 2. Setup Google API (Real or Mock)
        val envFile = File("../.env")
        val envMap = if (envFile.exists()) {
            envFile.readLines()
                .filter { it.contains("=") && !it.startsWith("#") }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
        } else emptyMap()

        val realAccessToken = envMap["GOOGLE_ACCESS_TOKEN"]
        val refreshToken = envMap["GOOGLE_REFRESH_TOKEN"]
        val clientId = envMap["GOOGLE_CLIENT_ID"]
        val clientSecret = envMap["GOOGLE_CLIENT_SECRET"]
        
        var activeAccessToken = realAccessToken

        // Proactively refresh if possible to avoid 401
        if (refreshToken != null && clientId != null && clientSecret != null) {
            println("REFRESHING ACCESS TOKEN...")
            val refreshClient = HttpClient(io.ktor.client.engine.java.Java)
            try {
                val refreshResponse = runBlocking {
                    refreshClient.post("https://oauth2.googleapis.com/token") {
                        setBody(FormDataContent(Parameters.build {
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("refresh_token", refreshToken)
                            append("grant_type", "refresh_token")
                        }))
                    }
                }
                if (refreshResponse.status.isSuccess()) {
                    val body = runBlocking { refreshResponse.bodyAsText() }
                    activeAccessToken = body.substringAfter("\"access_token\": \"").substringBefore("\"")
                    println("TOKEN REFRESHED SUCCESSFULLY.")
                } else {
                    val errorBody = runBlocking { refreshResponse.bodyAsText() }
                    println("TOKEN REFRESH FAILED: ${refreshResponse.status} - $errorBody")
                }
            } catch (e: Exception) {
                println("EXCEPTION DURING REFRESH: ${e.message}")
            } finally {
                refreshClient.close()
            }
        }
        
        val httpClient = if (activeAccessToken != null && realAccessToken != null) {
            HttpClient(io.ktor.client.engine.java.Java) {
                install(ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
        } else {
            val mockEngine = MockEngine { request ->
                if (request.method == io.ktor.http.HttpMethod.Delete) {
                    respond(content = "", status = HttpStatusCode.NoContent)
                } else if (request.method == io.ktor.http.HttpMethod.Get && request.url.encodedPath.contains("/events")) {
                    respond(
                        content = """{"items": [{"id": "old-event-1", "summary": "Old Event", "start": {"date": "2024-01-01"}, "end": {"date": "2024-01-01"}}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(content = """{"id": "mock-id"}""", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                }
            }
        }

        val syncService = GoogleCalendarSyncService(httpClient)
        val tokenRepo = GoogleTokenRepository(com.russhwolf.settings.MapSettings())
        tokenRepo.saveTokens(activeAccessToken ?: "mock-access", refreshToken ?: "mock-refresh")
        
        val googleRepo = GoogleRemoteCalendarRepository(syncService, tokenRepo)
        val icsSource = IcsCalendarSource(icsContent)

        // 3. Clear existing events (USE CAUTION IF RUNNING AGAINST REAL CALENDAR)
        if (activeAccessToken != null && realAccessToken != null) {
            println("WARNING: RUNNING AGAINST REAL GOOGLE CALENDAR. CLEARING EVENTS...")
        }
        try {
            runBlocking {
                googleRepo.clearCalendar("primary")
            }
        } catch (e: GoogleApiException) {
            println("FAILED TO CLEAR CALENDAR: ${e.statusCode} - ${e.responseBody}")
            throw e
        }

        // 4. Parse and Push
        val events = runBlocking { icsSource.getEvents() }
        events.isNotEmpty() shouldBe true
        
        var successCount = 0
        runBlocking {
            events.take(5).forEach { event -> 
                try {
                    googleRepo.saveEvent(event)
                    successCount++
                } catch (e: GoogleApiException) {
                    println("FAILED TO PUSH EVENT '${event.title}': ${e.statusCode} - ${e.responseBody}")
                    throw e
                } catch (e: Exception) {
                    println("Failed to push event '${event.title}': ${e.message}")
                }
            }
        }

        successCount shouldBe 5
    }
})
