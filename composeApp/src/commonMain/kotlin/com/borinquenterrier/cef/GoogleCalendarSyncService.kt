package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
class GoogleCalendarSyncService(private val httpClient: HttpClient) {

    private val baseUrl = "https://www.googleapis.com/calendar/v3"

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            throw GoogleApiException(response.status.value, response.bodyAsText())
        }
    }

    /**
     * Creates a new calendar for the user.
     */
    suspend fun createCalendar(accessToken: String, summary: String): String {
        val response = httpClient.post("$baseUrl/calendars") {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(mapOf("summary" to summary))
        }
        ensureSuccess(response)
        // Return the new calendar ID
        val body = response.body<GoogleCalendarDiscoveryItem>()
        return body.id
    }

    /**
     * Lists calendars available for the authenticated user.
     */
    suspend fun listCalendars(accessToken: String): List<RemoteCalendarMetadata> {
        val response = httpClient.get("$baseUrl/users/me/calendarList") {
            header("Authorization", "Bearer $accessToken")
        }
        ensureSuccess(response)
        val listResponse = response.body<GoogleCalendarListDiscoveryResponse>()
        return listResponse.items.map { RemoteCalendarMetadata(it.id, it.summary) }
    }

    /**
     * Synchronizes a CEF Event with a specific Google Calendar using the REST API.
     */
    suspend fun syncEvent(event: Event, accessToken: String, calendarId: String = "primary"): String {
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
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(googleEvent)
        }
        ensureSuccess(response)
        return response.bodyAsText()
    }

    /**
     * Deletes an event from a specific Google Calendar.
     */
    suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String) {
        val response = httpClient.delete("$baseUrl/calendars/$calendarId/events/$eventId") {
            header("Authorization", "Bearer $accessToken")
        }
        ensureSuccess(response)
    }

    /**
     * Fetches events from a specific Google Calendar.
     */
    suspend fun getEvents(accessToken: String, calendarId: String = "primary"): List<Event> {
        val response = httpClient.get("$baseUrl/calendars/$calendarId/events") {
            header("Authorization", "Bearer $accessToken")
        }
        ensureSuccess(response)
        val googleResponse = response.body<GoogleCalendarEventsResponse>()
        
        return googleResponse.items.map { item ->
            if (item.start.dateTime != null && item.end.dateTime != null) {
                // Handle various RFC3339 formats (Z, +HH:MM, or none)
                val cleanStart = item.start.dateTime.take(16) // "YYYY-MM-DDTHH:MM"
                val cleanEnd = item.end.dateTime.take(16)
                
                TimeEvent(
                    id = item.id,
                    title = item.summary ?: "Untitled Event",
                    source = EventSource.STUDENT,
                    date = LocalDate.parse(cleanStart.substringBefore("T")),
                    startTime = LocalTime.parse(cleanStart.substringAfter("T")),
                    endTime = LocalTime.parse(cleanEnd.substringAfter("T"))
                )
            } else {
                DayEvent(
                    id = item.id,
                    title = item.summary ?: "Untitled Event",
                    source = EventSource.STUDENT,
                    date = LocalDate.parse(item.start.date ?: "2024-01-01")
                )
            }
        }
    }
}

@Serializable
data class GoogleCalendarListDiscoveryResponse(
    val items: List<GoogleCalendarDiscoveryItem>
)

@Serializable
data class GoogleCalendarDiscoveryItem(
    val id: String,
    val summary: String
)

@Serializable
data class GoogleCalendarEventsResponse(
    val items: List<GoogleCalendarItem>
)

@Serializable
data class GoogleCalendarItem(
    val id: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val start: GoogleEventDateTime,
    val end: GoogleEventDateTime
)
