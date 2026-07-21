package com.borinquenterrier.cef.lti

import com.borinquenterrier.cef.OAuthStateStore
import com.borinquenterrier.cef.randomHexId
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect

/**
 * OIDC third-party-initiated login — LTI 1.3 launch step 1. The platform sends the browser here
 * first (GET or POST, per the spec) carrying `iss`/`login_hint`/`target_link_uri`; we mint a
 * state+nonce pair and redirect on to the platform's own `auth_login_url`, which is what actually
 * authenticates the user and posts the signed launch id_token back to /lti/launch.
 */
object LtiLoginHandler {
    suspend fun handleLogin(call: ApplicationCall, config: LtiPlatformConfig, appBaseUrl: String) {
        val params = if (call.request.httpMethod == HttpMethod.Post) {
            call.receiveParameters()
        } else {
            call.request.queryParameters
        }

        val iss = params["iss"]
        val loginHint = params["login_hint"]
        if (iss != config.issuer || loginHint == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unrecognized LTI platform or missing login_hint"))
            return
        }

        val nonce = randomHexId(32)
        val state = OAuthStateStore.create(nonce)

        val redirectUrl = URLBuilder(config.authLoginUrl).apply {
            parameters.append("scope", "openid")
            parameters.append("response_type", "id_token")
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", "$appBaseUrl/lti/launch")
            parameters.append("login_hint", loginHint)
            parameters.append("state", state)
            parameters.append("response_mode", "form_post")
            parameters.append("nonce", nonce)
            parameters.append("prompt", "none")
            params["lti_message_hint"]?.let { parameters.append("lti_message_hint", it) }
        }.buildString()

        call.respondRedirect(redirectUrl)
    }
}
