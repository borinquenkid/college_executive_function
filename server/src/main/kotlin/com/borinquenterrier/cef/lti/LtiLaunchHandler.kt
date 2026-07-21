package com.borinquenterrier.cef.lti

import com.borinquenterrier.cef.DirectoryDatabase
import com.borinquenterrier.cef.OAuthStateStore
import com.borinquenterrier.cef.StaffSession
import com.borinquenterrier.cef.UserSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlin.time.Clock

/**
 * LTI 1.3 Resource Link launch — step 2. The platform POSTs the signed id_token here (the
 * redirect_uri LtiLoginHandler sent it to). On success this is the *only* place a session ever
 * gets minted (see docs/adr/0006-lti-1.3-only-auth.md) — there's no separate signup step.
 */
object LtiLaunchHandler {
    suspend fun handleLaunch(
        call: ApplicationCall,
        verifier: LtiLaunchVerifier,
        directoryDatabase: DirectoryDatabase
    ) {
        val params = call.receiveParameters()
        val idToken = params["id_token"]
        val state = params["state"]
        if (idToken == null || state == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing id_token or state"))
            return
        }

        val nonce = OAuthStateStore.consume(state)
        if (nonce == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unknown or expired state"))
            return
        }

        val claims = try {
            verifier.verify(idToken, nonce)
        } catch (e: LtiLaunchVerificationException) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "launch verification failed"))
            return
        }

        val isStaff = isStaffRole(claims.roleUris)
        val epoch = directoryDatabase.recordLaunch(claims.studentId, isStaff, Clock.System.now().toEpochMilliseconds())

        call.sessions.set(UserSession(claims.studentId, epoch))
        if (isStaff) {
            // Everyone gets a UserSession (an instructor may also want their own app instance),
            // staff additionally get a StaffSession granting the /staff console.
            call.sessions.set(StaffSession(claims.studentId))
            call.respondRedirect("/staff/")
        } else {
            call.respondRedirect("/")
        }
    }
}
