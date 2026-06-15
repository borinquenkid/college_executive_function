package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Google Calendar API Event model for serialization.
 */
@Serializable
data class GoogleEvent(
    val summary: String,
    val description: String? = null,
    val start: GoogleEventDateTime,
    val end: GoogleEventDateTime
)

@Serializable
data class GoogleEventDateTime(
    val dateTime: String? = null, // RFC3339 format, used for timed events
    val date: String? = null, // yyyy-mm-dd format, used for all-day events
    val timeZone: String? = null
)

/**
 * Custom exception for Google API failures.
 */
class GoogleApiException(val statusCode: Int, val responseBody: String) :
    Exception("Google API Error ($statusCode): $responseBody")

/**
 * KMP-compatible service to sync events via Google Calendar REST API.
 */
class GoogleCalendarSyncService(
    private val httpClient: HttpClient,
    private val tokenRepository: GoogleTokenRepository,
    private val authService: GoogleAuthService,
    private val logger: Logger? = null
) {

    private val baseUrl = "https://www.googleapis.com/calendar/v3"

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            throw GoogleApiException(response.status.value, response.bodyAsText())
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val currentToken =
            tokenRepository.getAccessToken() ?: throw Exception("Not authenticated with Google")
        return try {
            block(currentToken)
        } catch (e: GoogleApiException) {
            if (e.statusCode == 401) {
                val refreshToken = tokenRepository.getRefreshToken() ?: throw e
                val newToken = authService.refreshAccessToken(refreshToken) ?: throw e
                tokenRepository.saveTokens(newToken, refreshToken)
                block(newToken)
            } else {
                throw e
            }
        }
    }

    /**
     * Creates a new calendar for the user.
     */
    suspend fun createCalendar(summary: String): String = withToken { token ->
        val response = httpClient.post("$baseUrl/calendars") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("summary" to summary))
        }
        ensureSuccess(response)
        // Return the new calendar ID
        val body = response.body<GoogleCalendarDiscoveryItem>()
        body.id
    }

    /**
     * Lists calendars available for the authenticated user.
     */
    suspend fun listCalendars(): List<RemoteCalendarMetadata> = withToken { token ->
        val response = httpClient.get("$baseUrl/users/me/calendarList") {
            header("Authorization", "Bearer $token")
        }
        ensureSuccess(response)
        val listResponse = response.body<GoogleCalendarListDiscoveryResponse>()
        listResponse.items.map { RemoteCalendarMetadata(it.id, it.summary ?: "Untitled Calendar") }
    }

    /**
     * Synchronizes a CEF Event with a specific Google Calendar using the REST API.
     */
    suspend fun syncEvent(event: Event, calendarId: String = "primary"): String =
        withToken { token ->
            val googleEvent = when (event) {
                is TimeEvent -> {
                    val startStr = "${event.date}T${event.startTime}:00Z"
                    val endStr = "${event.date}T${event.endTime}:00Z"
                    GoogleEvent(
                        summary = event.title,
                        start = GoogleEventDateTime(dateTime = startStr),
                        end = GoogleEventDateTime(dateTime = endStr)
                    )
                }

                is DayEvent -> {
                    GoogleEvent(
                        summary = event.title,
                        start = GoogleEventDateTime(date = event.date.toString()),
                        end = GoogleEventDateTime(date = event.date.toString())
                    )
                }
            }

            val response = httpClient.post("$baseUrl/calendars/$calendarId/events") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(googleEvent)
            }
            ensureSuccess(response)
            response.bodyAsText()
        }

    /**
     * Deletes an event from a specific Google Calendar.
     */
    suspend fun deleteEvent(calendarId: String, eventId: String) = withToken { token ->
        val response = httpClient.delete("$baseUrl/calendars/$calendarId/events/$eventId") {
            header("Authorization", "Bearer $token")
        }
        ensureSuccess(response)
    }

    private suspend fun fetchEventsPage(
        calendarId: String,
        pageToken: String?
    ): GoogleCalendarEventsResponse = withToken { token ->
        val response = httpClient.get("$baseUrl/calendars/$calendarId/events") {
            header("Authorization", "Bearer $token")
            pageToken?.let { parameter("pageToken", it) }
        }
        ensureSuccess(response)
        response.body<GoogleCalendarEventsResponse>()
    }

    /**
     * Fetches events from a specific Google Calendar (handles pagination).
     */
    suspend fun getEvents(calendarId: String = "primary"): List<Event> {
        val allEvents = mutableListOf<Event>()
        var pageToken: String? = null

        do {
            val responsePage = fetchEventsPage(calendarId, pageToken)
            allEvents.addAll(responsePage.items.map { item ->
                val start = item.start
                val end = item.end

                val updatedAt = item.updated?.let {
                    try {
                        Instant.parse(it).toEpochMilliseconds()
                    } catch (_: Exception) {
                        logger?.d("GoogleCalendarSyncService", "Failed to parse updatedAt: $it")
                        0L
                    }
                } ?: 0L

                if (start?.dateTime != null && end?.dateTime != null) {
                    // Handle various RFC3339 formats (Z, +HH:MM, or none)
                    val cleanStart = start.dateTime.take(16) // "YYYY-MM-DDTHH:MM"
                    val cleanEnd = end.dateTime.take(16)

                    TimeEvent(
                        id = item.id,
                        title = item.summary ?: "Untitled Event",
                        source = EventSource.STUDENT,
                        date = LocalDate.parse(cleanStart.substringBefore("T")),
                        startTime = LocalTime.parse(cleanStart.substringAfter("T")),
                        endTime = LocalTime.parse(cleanEnd.substringAfter("T")),
                        updatedAt = updatedAt
                    )
                } else {
                    DayEvent(
                        id = item.id,
                        title = item.summary ?: "Untitled Event",
                        source = EventSource.STUDENT,
                        date = LocalDate.parse(start?.date ?: "2024-01-01"),
                        updatedAt = updatedAt
                    )
                }
            })
            pageToken = responsePage.nextPageToken
        } while (pageToken != null)

        return allEvents
    }
}

@Serializable
data class GoogleCalendarListDiscoveryResponse(
    val items: List<GoogleCalendarDiscoveryItem>
)

@Serializable
data class GoogleCalendarDiscoveryItem(
    val id: String,
    val summary: String? = null
)

@Serializable
data class GoogleCalendarEventsResponse(
    val items: List<GoogleCalendarItem>,
    val nextPageToken: String? = null
)

@Serializable
data class GoogleCalendarItem(
    val id: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val start: GoogleEventDateTime? = null,
    val end: GoogleEventDateTime? = null,
    val updated: String? = null
)
