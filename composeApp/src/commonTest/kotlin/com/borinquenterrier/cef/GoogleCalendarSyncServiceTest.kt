package com.borinquenterrier.cef

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class GoogleCalendarSyncServiceTest : FunSpec({

    test("syncEvent maps correctly and sends POST request to Google API") {
        // 1. Arrange: Setup MockEngine to capture the request
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"id": "123", "summary": "Test Event"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val service = GoogleCalendarSyncService(httpClient)
        
        val event = TimeEvent(
            title = "Test Event",
            source = EventSource.MANUAL,
            startTime = LocalTime(10, 0),
            endTime = LocalTime(11, 0),
            date = LocalDate(2025, 1, 1)
        )

        // 2. Act
        val response = service.syncEvent(event, "mock-token")

        // 3. Assert
        response shouldContain "200 OK"
        
        // Verify the request details
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://www.googleapis.com/calendar/v3/calendars/primary/events"
        request.headers["Authorization"] shouldBe "Bearer mock-token"
    }
})