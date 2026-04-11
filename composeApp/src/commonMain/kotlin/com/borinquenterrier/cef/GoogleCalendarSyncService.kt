package com.borinquenterrier.cef

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
 * KMP-compatible service to sync events via Google Calendar REST API.
 */
class GoogleCalendarSyncService(private val httpClient: HttpClient) {

    private val baseUrl = "https://www.googleapis.com/calendar/v3"

    /**
     * Synchronizes a CEF Event with Google Calendar using the REST API.
     * Note: Requires an OAuth2 [accessToken].
     */
    suspend fun syncEvent(event: Event, accessToken: String): String {
        val googleEvent = when (event) {
            is TimeEvent -> {
                // Formatting to a very basic string without proper timezone handling for MVP
                val startStr = "${event.date}T${event.startTime}:00" 
                val endStr = "${event.date}T${event.endTime}:00" 
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

        val response = httpClient.post("$baseUrl/calendars/primary/events") {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(googleEvent)
        }
        
        return response.toString()
    }
}
