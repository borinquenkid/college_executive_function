package com.borinquenterrier.cef

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Real (non-mocked) end-to-end check of the session-based auth model added to close the
 * "any client can claim to be any student" gap: uses a real ServerContainerFactory tenant (no
 * testContainer injection, unlike WebIngestionIntegrationTest), so it actually exercises
 * resolveStudentId(), the Sessions plugin, and the rate limiter — not just route wiring.
 */
class AuthSessionIntegrationTest {

    private val createdFactories = mutableListOf<ServerContainerFactory>()

    private fun newFactory(): ServerContainerFactory {
        val baseDir = Files.createTempDirectory("cef-auth-session-test").toFile()
        return ServerContainerFactory(tenantBaseDir = baseDir.absolutePath).also { createdFactories += it }
    }

    @AfterTest
    fun tearDown() = runBlocking {
        createdFactories.forEach { it.closeAll() }
        createdFactories.clear()
    }

    @Test
    fun `unauthenticated request is rejected`() = testApplication {
        val factory = newFactory()
        application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }

        val response = client.get("/api/sources")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `auth start creates a session once and reuses it`() = testApplication {
        val factory = newFactory()
        application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }
        val authedClient = createClient { install(HttpCookies) }

        val first = authedClient.post("/api/auth/start")
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(first.bodyAsText().contains("created"))

        val authedGet = authedClient.get("/api/sources")
        assertEquals(HttpStatusCode.OK, authedGet.status)

        val second = authedClient.post("/api/auth/start")
        assertEquals(HttpStatusCode.OK, second.status)
        assertTrue(second.bodyAsText().contains("\"status\":\"ok\""), "second call should reuse, not recreate: ${second.bodyAsText()}")
    }

    @Test
    fun `two sessions never see each other's data`() = testApplication {
        val factory = newFactory()
        application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }
        val studentA = createClient { install(HttpCookies) }
        val studentB = createClient { install(HttpCookies) }

        studentA.post("/api/auth/start")
        studentB.post("/api/auth/start")

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
    fun `auth start is rate limited per IP`() = testApplication {
        val factory = newFactory()
        application { module(containerFactory = { studentId -> factory.containerFor(studentId) }) }

        val statuses = (1..6).map { client.post("/api/auth/start").status }

        assertTrue(statuses.take(5).all { it == HttpStatusCode.OK }, "first 5 calls should succeed: $statuses")
        assertEquals(HttpStatusCode.TooManyRequests, statuses[5], "6th call within the window should be rate-limited: $statuses")
    }
}
