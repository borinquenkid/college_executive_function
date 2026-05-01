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

    test("syncEvent maps correctly and sends POST request to Google API with calendarId") {
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

        val response = service.syncEvent(event, "mock-token", "school-cal")

        response shouldContain "123"
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://www.googleapis.com/calendar/v3/calendars/school-cal/events"
        request.headers["Authorization"] shouldBe "Bearer mock-token"
    }

    test("getEvents fetches and maps events correctly") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                {
                    "items": [
                        {
                            "summary": "Existing Event",
                            "start": { "dateTime": "2025-01-01T10:00:00Z" },
                            "end": { "dateTime": "2025-01-01T11:00:00Z" }
                        }
                    ]
                }
                """.trimIndent(),
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
        val events = service.getEvents("mock-token", "school-cal")

        events.size shouldBe 1
        events.first().title shouldBe "Existing Event"
        (events.first() as TimeEvent).date shouldBe LocalDate(2025, 1, 1)
        
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://www.googleapis.com/calendar/v3/calendars/school-cal/events"
    }

    test("listCalendars fetches and maps calendar list correctly") {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                {
                    "items": [
                        { "id": "cal-1", "summary": "Primary" },
                        { "id": "cal-2", "summary": "School" }
                    ]
                }
                """.trimIndent(),
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
        val calendars = service.listCalendars("mock-token")

        calendars.size shouldBe 2
        calendars[0].id shouldBe "cal-1"
        calendars[1].name shouldBe "School"
        
        val request = mockEngine.requestHistory.first()
        request.url.toString() shouldBe "https://www.googleapis.com/calendar/v3/users/me/calendarList"
    }
})
