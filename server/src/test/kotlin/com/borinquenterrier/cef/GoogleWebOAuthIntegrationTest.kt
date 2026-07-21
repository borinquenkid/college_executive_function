package com.borinquenterrier.cef

import com.borinquenterrier.cef.lti.LtiTestSupport
import com.borinquenterrier.cef.lti.loginViaLti
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Covers the self-serve Google Calendar web OAuth flow (docs/adr/0008): the real token exchange
 * with Google is swapped for a fake via GoogleWebOAuthService's injectable tokenExchanger, so
 * these never make a live HTTPS call — same pattern LtiTestSupport uses for LTI's JWKS.
 */
class GoogleWebOAuthIntegrationTest {

    private val createdFactories = mutableListOf<ServerContainerFactory>()

    private fun newFactory(): ServerContainerFactory {
        val baseDir = Files.createTempDirectory("cef-google-oauth-test").toFile()
        return ServerContainerFactory(tenantBaseDir = baseDir.absolutePath).also { createdFactories += it }
    }

    @AfterTest
    fun tearDown() = runBlocking {
        createdFactories.forEach { it.closeAll() }
        createdFactories.clear()
    }

    private val testGoogleConfig = GoogleWebOAuthConfig(clientId = "test-web-client-id", clientSecret = "test-web-client-secret")

    private fun io.ktor.server.application.Application.installModule(
        factory: ServerContainerFactory,
        googleWebOAuthService: GoogleWebOAuthService? = null
    ) {
        module(
            containerFactory = { studentId -> factory.containerFor(studentId) },
            ltiPlatformConfig = LtiTestSupport.config,
            ltiVerifier = LtiTestSupport.verifier(),
            directoryDatabase = factory.directoryDatabase,
            dbFactory = factory.dbFactory,
            appBaseUrl = "https://test.example.edu",
            googleWebOAuthService = googleWebOAuthService
        )
    }

    @Test
    fun `start requires a session`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        val response = client.get("/api/auth/google/start")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `start is unavailable when Google web OAuth isn't configured`() = testApplication {
        val factory = newFactory()
        application { installModule(factory, googleWebOAuthService = null) }
        val client = createClient { install(HttpCookies) }
        client.loginViaLti(subject = "alice")

        val response = client.get("/api/auth/google/start")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `start redirects to Google's consent screen with the expected params`() = testApplication {
        val factory = newFactory()
        val service = GoogleWebOAuthService(testGoogleConfig, "https://test.example.edu/api/auth/google/callback")
        application { installModule(factory, googleWebOAuthService = service) }
        val client = createClient { install(HttpCookies); followRedirects = false }
        client.loginViaLti(subject = "alice")

        val response = client.get("/api/auth/google/start")
        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[io.ktor.http.HttpHeaders.Location]!!
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/auth"), "should redirect to Google: $location")
        assertTrue(location.contains("client_id=test-web-client-id"), "location: $location")
        assertTrue(location.contains("redirect_uri=https://test.example.edu/api/auth/google/callback"), "location: $location")
        assertTrue(location.contains("access_type=offline"), "location: $location")
        assertTrue(location.contains("prompt=consent"), "location: $location")
        assertTrue(location.contains("state="), "location: $location")
    }

    @Test
    fun `callback rejects a missing or mismatched state`() = testApplication {
        val factory = newFactory()
        val service = GoogleWebOAuthService(testGoogleConfig, "https://test.example.edu/api/auth/google/callback")
        application { installModule(factory, googleWebOAuthService = service) }
        val client = createClient { install(HttpCookies) }
        client.loginViaLti(subject = "alice")

        val missingState = client.get("/api/auth/google/callback?code=abc")
        assertEquals(HttpStatusCode.BadRequest, missingState.status)

        val badState = client.get("/api/auth/google/callback?code=abc&state=never-issued")
        assertEquals(HttpStatusCode.Unauthorized, badState.status)
    }

    @Test
    fun `a successful callback saves tokens and flips the linked status`() = testApplication {
        val factory = newFactory()
        val fakeTokenResponse = GoogleTokenResponse().apply {
            accessToken = "fake-access-token"
            refreshToken = "fake-refresh-token"
        }
        val service = GoogleWebOAuthService(
            testGoogleConfig,
            "https://test.example.edu/api/auth/google/callback",
            tokenExchanger = { _ -> fakeTokenResponse }
        )
        application { installModule(factory, googleWebOAuthService = service) }
        val client = createClient { install(HttpCookies); followRedirects = false }
        client.loginViaLti(subject = "alice")

        val statusBefore = client.get("/api/auth/google/status").bodyAsText()
        assertTrue(statusBefore.contains("\"linked\":false"), "should start unlinked: $statusBefore")

        val start = client.get("/api/auth/google/start")
        val state = Regex("state=([a-f0-9]+)").find(start.headers[io.ktor.http.HttpHeaders.Location]!!)!!.groupValues[1]

        val callback = client.get("/api/auth/google/callback?code=fake-code&state=$state")
        assertEquals(HttpStatusCode.Found, callback.status)
        assertEquals("/", callback.headers[io.ktor.http.HttpHeaders.Location])

        val statusAfter = client.get("/api/auth/google/status").bodyAsText()
        assertTrue(statusAfter.contains("\"linked\":true"), "should be linked after a successful callback: $statusAfter")
    }

    @Test
    fun `a token exchange failure does not link the account`() = testApplication {
        val factory = newFactory()
        val service = GoogleWebOAuthService(
            testGoogleConfig,
            "https://test.example.edu/api/auth/google/callback",
            tokenExchanger = { _ -> throw RuntimeException("simulated Google outage") }
        )
        application { installModule(factory, googleWebOAuthService = service) }
        val client = createClient { install(HttpCookies); followRedirects = false }
        client.loginViaLti(subject = "alice")

        val start = client.get("/api/auth/google/start")
        val state = Regex("state=([a-f0-9]+)").find(start.headers[io.ktor.http.HttpHeaders.Location]!!)!!.groupValues[1]

        val callback = client.get("/api/auth/google/callback?code=fake-code&state=$state")
        assertEquals(HttpStatusCode.BadGateway, callback.status)

        val status = client.get("/api/auth/google/status").bodyAsText()
        assertTrue(status.contains("\"linked\":false"), "must not be linked after a failed exchange: $status")
    }
}
