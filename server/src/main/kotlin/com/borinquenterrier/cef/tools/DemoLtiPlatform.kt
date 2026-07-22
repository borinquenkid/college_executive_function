package com.borinquenterrier.cef.tools

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

const val DEMO_LTI_ISSUER = "https://demo-lms.local"
const val DEMO_LTI_CLIENT_ID = "demo-client"
const val DEMO_LTI_DEPLOYMENT_ID = "demo-deployment"
private const val DEMO_LTI_KEY_ID = "demo-key-1"
private const val LEARNER_ROLE_URI = "http://purl.imsglobal.org/vocab/lis/v2/membership#Learner"
private const val INSTRUCTOR_ROLE_URI = "http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"

/**
 * Stands in for a real LMS so `:server:run` can be exercised interactively in a browser —
 * ADR 0006 made LTI 1.3 the only login path, and DEPLOYMENT.md's "Local Development" section
 * otherwise only offers a real LMS sandbox (needs an ngrok tunnel, HTTPS) or the automated test
 * suite (`LtiTestSupport`, not browser-drivable). This plays the platform side of the same
 * handshake `LtiTestSupport` drives in tests, but over real HTTP: it mints its own RSA keypair,
 * serves it as a JWKS, and answers the `auth_login_url` step by signing a launch id_token and
 * auto-POSTing it back to the real server's `/lti/launch`.
 *
 * Never used against a real deployment's LTI config — point a locally-run server's CEF_LTI_*
 * env vars at *this* process's issuer/client/deployment/jwks values (printed on startup), never
 * at production `.env`.
 */
fun main() {
    val mockPort = System.getenv("DEMO_LTI_MOCK_PORT")?.toIntOrNull() ?: 9099
    val appBaseUrl = System.getenv("CEF_APP_BASE_URL")?.trimEnd('/') ?: "http://localhost:8080"

    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val publicKey = keyPair.public as RSAPublicKey
    val privateKey = keyPair.private as RSAPrivateKey

    println(
        """
        |Demo LTI mock platform on http://localhost:$mockPort — stands in for a real LMS, see
        |DEPLOYMENT.md's "Local Development" section.
        |
        |Point the real server (a SEPARATE `:server:run`, never a real deployment's `.env`) at:
        |  CEF_LTI_ISSUER=$DEMO_LTI_ISSUER
        |  CEF_LTI_CLIENT_ID=$DEMO_LTI_CLIENT_ID
        |  CEF_LTI_DEPLOYMENT_IDS=$DEMO_LTI_DEPLOYMENT_ID
        |  CEF_LTI_AUTH_LOGIN_URL=http://localhost:$mockPort/auth
        |  CEF_LTI_JWKS_URL=http://localhost:$mockPort/jwks
        |  CEF_APP_BASE_URL=$appBaseUrl
        |
        |Then open, as a learner:
        |  $appBaseUrl/lti/login?iss=$DEMO_LTI_ISSUER&login_hint=demo-student
        |Or as an instructor (lands on the staff console instead):
        |  $appBaseUrl/lti/login?iss=$DEMO_LTI_ISSUER&login_hint=demo-student&role=instructor
        """.trimMargin()
    )

    embeddedServer(Netty, port = mockPort) {
        routing {
            get("/jwks") {
                call.respondText(
                    contentType = ContentType.Application.Json,
                    text = """{"keys":[{"kid":"$DEMO_LTI_KEY_ID","kty":"RSA","alg":"RS256","use":"sig",""" +
                        """"n":"${publicKey.modulus.toBase64Url()}","e":"${publicKey.publicExponent.toBase64Url()}"}]}"""
                )
            }
            get("/auth") {
                val params = call.request.queryParameters
                val redirectUri = params["redirect_uri"]
                val state = params["state"]
                val nonce = params["nonce"]
                if (redirectUri == null || state == null || nonce == null) {
                    call.respond(HttpStatusCode.BadRequest, "missing redirect_uri, state, or nonce")
                    return@get
                }
                val roleUri = if (params["role"] == "instructor") INSTRUCTOR_ROLE_URI else LEARNER_ROLE_URI

                val idToken = JWT.create()
                    .withKeyId(DEMO_LTI_KEY_ID)
                    .withIssuer(DEMO_LTI_ISSUER)
                    .withAudience(DEMO_LTI_CLIENT_ID)
                    .withSubject("demo-student")
                    .withClaim("nonce", nonce)
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest")
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0")
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id", DEMO_LTI_DEPLOYMENT_ID)
                    .withClaim("https://purl.imsglobal.org/spec/lti/claim/roles", listOf(roleUri))
                    .withIssuedAt(Date())
                    .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                    .sign(Algorithm.RSA256(publicKey, privateKey))

                // Auto-submitting form POST — same shape a real LMS's form_post response_mode
                // produces, so LtiLaunchHandler is exercised exactly as it is in production.
                call.respondText(
                    contentType = ContentType.Text.Html,
                    text = """
                        |<html><body onload="document.forms[0].submit()">
                        |<form method="POST" action="${redirectUri.htmlEscape()}">
                        |<input type="hidden" name="id_token" value="${idToken.htmlEscape()}">
                        |<input type="hidden" name="state" value="${state.htmlEscape()}">
                        |</form>
                        |Signing you in via the demo LTI platform&hellip;
                        |</body></html>
                        """.trimMargin()
                )
            }
        }
    }.start(wait = true)
}

/** JWK's "n"/"e" fields are unsigned big-endian base64url — BigInteger.toByteArray() can prepend
 *  a 0x00 sign byte that must be stripped or the reconstructed key is wrong. */
private fun BigInteger.toBase64Url(): String {
    var bytes = toByteArray()
    if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes = bytes.copyOfRange(1, bytes.size)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun String.htmlEscape(): String =
    replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
