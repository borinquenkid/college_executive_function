package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class WebSettings(
    val apiKey: String? = null,
    val studyPreferences: StudyPreferences? = null
)

object WebSettingsHandler {
    suspend fun handleGetSettings(call: ApplicationCall, container: DependencyContainer) {
        val apiKey = container.settings.getString("CEF_GEMINI_API_KEY", "")
        val prefs = container.preferencesRepository.getPreferences()
        call.respond(WebSettings(apiKey = apiKey, studyPreferences = prefs))
    }

    suspend fun handleSaveSettings(call: ApplicationCall, container: DependencyContainer) {
        val payload = call.receive<WebSettings>()
        payload.apiKey?.let {
            container.settings.putString("CEF_GEMINI_API_KEY", it)
            container.settings.putString("GEMINI_API_KEY", it)
        }
        payload.studyPreferences?.let {
            container.preferencesRepository.savePreferences(it)
        }
        call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
    }

    suspend fun handleGetGoogleAuthStatus(call: ApplicationCall, container: DependencyContainer) {
        val hasToken = container.tokenRepository.hasTokens()
        call.respond(mapOf("linked" to hasToken))
    }

    suspend fun handleGetCalendars(call: ApplicationCall, container: DependencyContainer) {
        val calendars = try {
            container.remoteRepository.getAvailableCalendars()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to (e.message ?: "Not connected")))
            return
        }
        call.respond(calendars)
    }

    suspend fun handleCreateCalendar(call: ApplicationCall, container: DependencyContainer) {
        val body = call.receive<Map<String, String>>()
        val name = body["name"]
        if (name.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing calendar name"))
            return
        }
        val newId = try {
            container.syncService.createCalendar(name)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to (e.message ?: "Create failed")))
            return
        }
        call.respond(mapOf("id" to newId, "name" to name))
    }
}
