package com.borinquenterrier.cef

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect

object GoogleWebOAuthHandler {
    suspend fun handleStart(call: ApplicationCall, studentId: String, service: GoogleWebOAuthService?) {
        if (service == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Google Calendar sync isn't configured for this deployment."))
            return
        }
        // Bound to studentId, not just a random CSRF token: prevents an attacker who starts their
        // own flow from tricking a different logged-in student into completing it and linking
        // their account to the attacker's Google credentials (see docs/adr/0008).
        val state = OAuthStateStore.create(studentId)
        call.respondRedirect(service.authorizationUrl(state))
    }

    suspend fun handleCallback(
        call: ApplicationCall,
        studentId: String,
        service: GoogleWebOAuthService?,
        container: DependencyContainer
    ) {
        if (service == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Google Calendar sync isn't configured for this deployment."))
            return
        }
        val code = call.request.queryParameters["code"]
        val state = call.request.queryParameters["state"]
        if (code == null || state == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing code or state"))
            return
        }
        val stateStudentId = OAuthStateStore.consume(state)
        if (stateStudentId == null || stateStudentId != studentId) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unknown, expired, or mismatched state"))
            return
        }
        val tokenResponse = try {
            service.exchangeCode(code)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Google token exchange failed: ${e.message}"))
            return
        }
        container.tokenRepository.saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
        call.respondRedirect("/")
    }
}
