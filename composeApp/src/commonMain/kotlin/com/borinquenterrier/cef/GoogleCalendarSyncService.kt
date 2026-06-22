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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Google Calendar API Event model for serialization.
 */
@Serializable
data class GoogleEvent(
    val summary: String,
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
    Exception("Google API Error ($statusCode): $responseBody") {

    fun toCalendarException(calendarId: String): Throwable = when (statusCode) {
        404 -> CalendarNotFoundException(
            calendarId = calendarId,
            message = "Calendar '$calendarId' no longer exists or has been deleted on Google Calendar. " +
                    "Please re-link your calendar or use a different calendar."
        )
        403 -> CalendarNotFoundException(
            calendarId = calendarId,
            message = "No longer have access to calendar '$calendarId'. " +
                    "The calendar owner may have revoked your access."
        )
        else -> this
    }
}

/**
 * KMP-compatible service to sync events via Google Calendar REST API.
 */
class GoogleCalendarSyncService(
    private val httpClient: HttpClient,
    private val tokenService: GoogleTokenService
) {

    private val baseUrl = "https://www.googleapis.com/calendar/v3"

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            throw GoogleApiException(response.status.value, response.bodyAsText())
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> T): T =
        tokenService.withToken(block)

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
                    val tz = TimeZone.currentSystemDefault()
                    val startStr = LocalDateTime(event.date, event.startTime).toInstant(tz).toString()
                    val endStr = LocalDateTime(event.date, event.endTime).toInstant(tz).toString()
                    GoogleEvent(
                        summary = event.title,
                        start = GoogleEventDateTime(dateTime = startStr),
                        end = GoogleEventDateTime(dateTime = endStr)
                    )
                }

                is DayEvent -> {
                    // Google Calendar uses exclusive end dates for all-day events
                    val endDate = event.date.plus(1, DateTimeUnit.DAY)
                    GoogleEvent(
                        summary = event.title,
                        start = GoogleEventDateTime(date = event.date.toString()),
                        end = GoogleEventDateTime(date = endDate.toString())
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
                    } catch (e: Exception) {
                        0L
                    }
                } ?: 0L

                if (start?.dateTime != null && end?.dateTime != null) {
                    val tz = TimeZone.currentSystemDefault()

                    // RFC3339 always carries a timezone offset (Z or ±HH:MM).
                    // Parse as Instant so the offset is honoured, then convert to
                    // the user's local timezone. Fall back to treating the string
                    // as a naive local datetime if the offset is absent.
                    fun parseDateTime(raw: String): LocalDateTime = try {
                        Instant.parse(raw).toLocalDateTime(tz)
                    } catch (e: Exception) {
                        val clean = raw.take(16)
                        LocalDateTime(
                            LocalDate.parse(clean.substringBefore("T")),
                            LocalTime.parse(clean.substringAfter("T"))
                        )
                    }

                    val startDt = parseDateTime(start.dateTime)
                    val endDt = parseDateTime(end.dateTime)

                    TimeEvent(
                        id = item.id,
                        title = item.summary ?: "Untitled Event",
                        source = EventSource.STUDENT,
                        date = startDt.date,
                        startTime = startDt.time,
                        endTime = endDt.time,
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
