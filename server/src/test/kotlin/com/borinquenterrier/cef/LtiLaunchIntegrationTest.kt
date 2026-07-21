package com.borinquenterrier.cef

import com.borinquenterrier.cef.lti.INSTRUCTOR_ROLE
import com.borinquenterrier.cef.lti.LEARNER_ROLE
import com.borinquenterrier.cef.lti.LtiTestSupport
import com.borinquenterrier.cef.lti.loginViaLti
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Real (non-mocked) end-to-end check of the LTI 1.3-only auth model (docs/adr/0006): uses a real
 * ServerContainerFactory tenant (no testContainer injection, unlike WebIngestionIntegrationTest),
 * so it exercises the actual JWT verification in LtiLaunchVerifier, session minting, and the
 * rate limiter — not just route wiring. LtiTestSupport supplies a locally-generated RSA keypair
 * and a fake JwkProvider so no live network call to a real LMS is ever made.
 */
class LtiLaunchIntegrationTest {

    private val createdFactories = mutableListOf<ServerContainerFactory>()

    private fun newFactory(): ServerContainerFactory {
        val baseDir = Files.createTempDirectory("cef-lti-launch-test").toFile()
        return ServerContainerFactory(tenantBaseDir = baseDir.absolutePath).also { createdFactories += it }
    }

    @AfterTest
    fun tearDown() = runBlocking {
        createdFactories.forEach { it.closeAll() }
        createdFactories.clear()
    }

    private fun io.ktor.server.application.Application.installModule(factory: ServerContainerFactory) {
        module(
            containerFactory = { studentId -> factory.containerFor(studentId) },
            ltiPlatformConfig = LtiTestSupport.config,
            ltiVerifier = LtiTestSupport.verifier(),
            directoryDatabase = factory.directoryDatabase,
            dbFactory = factory.dbFactory,
            appBaseUrl = "https://test.example.edu"
        )
    }

    @Test
    fun `unauthenticated request is rejected`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        val response = client.get("/api/sources")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a valid Learner launch grants access and lands on the root app`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val authedClient = createClient { install(HttpCookies) }

        val launch = authedClient.loginViaLti(subject = "alice", roles = listOf(LEARNER_ROLE))
        assertEquals(HttpStatusCode.Found, launch.status)
        assertEquals("/", launch.headers[io.ktor.http.HttpHeaders.Location])

        val sourcesResponse = authedClient.get("/api/sources")
        assertEquals(HttpStatusCode.OK, sourcesResponse.status)
    }

    @Test
    fun `a valid Instructor launch also grants the staff console and redirects there`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val authedClient = createClient { install(HttpCookies) }

        val launch = authedClient.loginViaLti(subject = "prof-jones", roles = listOf(INSTRUCTOR_ROLE))
        assertEquals(HttpStatusCode.Found, launch.status)
        assertEquals("/staff/", launch.headers[io.ktor.http.HttpHeaders.Location])

        // Instructors still get their own app instance too — everyone gets a UserSession
        // regardless of role, staff additionally get a StaffSession (Workstream B).
        val sourcesResponse = authedClient.get("/api/sources")
        assertEquals(HttpStatusCode.OK, sourcesResponse.status)
    }

    @Test
    fun `two students launched via LTI never see each other's data`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val studentA = createClient { install(HttpCookies) }
        val studentB = createClient { install(HttpCookies) }

        studentA.loginViaLti(subject = "alice")
        studentB.loginViaLti(subject = "bob")

        studentA.post("/api/settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(WebSettings(studyPreferences = StudyPreferences(studyStartHour = 6))))
        }
        studentB.post("/api/settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(WebSettings(studyPreferences = StudyPreferences(studyStartHour = 20))))
        }

        val aSettings = studentA.get("/api/settings").bodyAsText()
        val bSettings = studentB.get("/api/settings").bodyAsText()

        assertTrue(aSettings.contains("\"studyStartHour\":6"), "student A should see their own prefs: $aSettings")
        assertTrue(bSettings.contains("\"studyStartHour\":20"), "student B should see their own prefs: $bSettings")
        assertTrue(!aSettings.contains("\"studyStartHour\":20"), "student A must not see student B's prefs: $aSettings")
    }

    @Test
    fun `the same subject launching twice reuses the same tenant`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val firstSession = createClient { install(HttpCookies) }
        val secondSession = createClient { install(HttpCookies) }

        firstSession.loginViaLti(subject = "alice")
        firstSession.post("/api/settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(WebSettings(studyPreferences = StudyPreferences(studyStartHour = 9))))
        }

        // A second, independent browser session (e.g. cleared cookies) re-launching with the same
        // LTI subject must land back on the same tenant data — this is the "recovery" property
        // LTI re-launch gives for free, in place of the old email/SMTP recovery workstream.
        secondSession.loginViaLti(subject = "alice")
        val settings = secondSession.get("/api/settings").bodyAsText()
        assertTrue(settings.contains("\"studyStartHour\":9"), "re-launching should restore the same tenant: $settings")
    }

    @Test
    fun `lti login redirects to the platform's auth endpoint with the expected OIDC params`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        // The redirect target is a real (fake, but external-looking) LMS origin — the test
        // client's transport can only route requests to the app under test, so it must not try
        // to actually follow this redirect.
        val client = createClient { followRedirects = false }

        val response = client.get(
            "/lti/login?iss=${LtiTestSupport.config.issuer}&login_hint=some-hint&target_link_uri=https://test.example.edu/"
        )

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[io.ktor.http.HttpHeaders.Location]!!
        assertTrue(location.startsWith(LtiTestSupport.config.authLoginUrl), "should redirect to the platform: $location")
        assertTrue(location.contains("client_id=${LtiTestSupport.config.clientId}"))
        assertTrue(location.contains("login_hint=some-hint"))
        assertTrue(location.contains("response_type=id_token"))
        assertTrue(location.contains("state="))
        assertTrue(location.contains("nonce="))
    }

    @Test
    fun `lti login rejects an unrecognized issuer`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        val response = client.get("/lti/login?iss=https://not-our-lms.example.edu&login_hint=x")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `lti login is rate limited per IP`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val client = createClient { followRedirects = false }

        val statuses = (1..6).map {
            client.get("/lti/login?iss=${LtiTestSupport.config.issuer}&login_hint=x").status
        }

        assertTrue(statuses.take(5).all { it == HttpStatusCode.Found }, "first 5 calls should succeed: $statuses")
        assertEquals(HttpStatusCode.TooManyRequests, statuses[5], "6th call within the window should be rate-limited: $statuses")
    }

    @Test
    fun `lti launch rejects a token with a bad signature`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        // A syntactically well-formed but unsigned/garbage JWT — never matches any real kid.
        val badToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImJvZ3VzIn0.eyJzdWIiOiJhbGljZSJ9.bm90LWEtcmVhbC1zaWduYXR1cmU"
        val response = client.post("/lti/launch") {
            setBody(FormDataContent(Parameters.build {
                append("id_token", badToken)
                append("state", "irrelevant")
            }))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `lti launch rejects an unregistered deployment_id`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        val response = client.loginViaLti(deploymentId = "some-other-deployment-nobody-registered")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `lti launch rejects a replayed state`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val authedClient = createClient { install(HttpCookies) }

        // OAuthStateStore.consume() is single-use — the second attempt with the identical
        // state+token must fail even though the token itself is otherwise perfectly valid.
        val nonce = randomHexId(32)
        val state = OAuthStateStore.create(nonce)
        val idToken = LtiTestSupport.signLaunchJwt(nonce = nonce)

        val first = authedClient.post("/lti/launch") {
            setBody(FormDataContent(Parameters.build {
                append("id_token", idToken)
                append("state", state)
            }))
        }
        assertEquals(HttpStatusCode.Found, first.status)

        val replay = authedClient.post("/lti/launch") {
            setBody(FormDataContent(Parameters.build {
                append("id_token", idToken)
                append("state", state)
            }))
        }
        assertEquals(HttpStatusCode.Unauthorized, replay.status)
    }

    @Test
    fun `lti launch rejects an unknown or expired state`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        val idToken = LtiTestSupport.signLaunchJwt(nonce = "some-nonce-with-no-matching-state")
        val response = client.post("/lti/launch") {
            setBody(FormDataContent(Parameters.build {
                append("id_token", idToken)
                append("state", "never-issued-state")
            }))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
