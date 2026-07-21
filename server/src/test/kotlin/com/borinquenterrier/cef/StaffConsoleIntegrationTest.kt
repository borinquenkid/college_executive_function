package com.borinquenterrier.cef

import com.borinquenterrier.cef.lti.INSTRUCTOR_ROLE
import com.borinquenterrier.cef.lti.LEARNER_ROLE
import com.borinquenterrier.cef.lti.LtiTestSupport
import com.borinquenterrier.cef.lti.loginViaLti
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Covers the staff console (docs/adr/0007-staff-console-via-lti-roles.md): role-gated access to
 * the /api/staff endpoints, the coarse-metadata-only response shape, and the session_epoch-based
 * force-reset mechanism.
 */
class StaffConsoleIntegrationTest {

    private val createdFactories = mutableListOf<ServerContainerFactory>()

    private fun newFactory(): ServerContainerFactory {
        val baseDir = Files.createTempDirectory("cef-staff-console-test").toFile()
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
    fun `a Learner cannot reach the staff console`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val client = createClient { install(HttpCookies) }
        client.loginViaLti(subject = "alice", roles = listOf(LEARNER_ROLE))

        val response = client.get("/api/staff/students")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `an unauthenticated caller cannot reach the staff console`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }

        val response = client.get("/api/staff/students")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `an Instructor can list students with coarse metadata only`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val staffClient = createClient { install(HttpCookies) }
        val studentClient = createClient { install(HttpCookies) }

        studentClient.loginViaLti(subject = "alice", roles = listOf(LEARNER_ROLE))
        staffClient.loginViaLti(subject = "prof-jones", roles = listOf(INSTRUCTOR_ROLE))

        val response = staffClient.get("/api/staff/students")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"createdAtMillis\""), "should include coarse metadata: $body")
        // The response must never include anything beyond studentId/createdAtMillis/lastActiveMillis
        // — no calendar/source/chat content, no settings, no API keys.
        assertFalse(body.contains("CEF_GEMINI_API_KEY"), "must not leak tenant settings: $body")

        // Staff accounts themselves aren't listed as "students".
        assertFalse(body.contains("prof-jones"), "staff shouldn't appear in the student list: $body")
    }

    @Test
    fun `resetting a session invalidates the student's existing cookie on their next request`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val staffClient = createClient { install(HttpCookies) }
        val studentClient = createClient { install(HttpCookies) }

        studentClient.loginViaLti(subject = "alice", roles = listOf(LEARNER_ROLE))
        staffClient.loginViaLti(subject = "prof-jones", roles = listOf(INSTRUCTOR_ROLE))

        // Confirm the student session works before the reset.
        assertEquals(HttpStatusCode.OK, studentClient.get("/api/sources").status)

        val students = staffClient.get("/api/staff/students").bodyAsText()
        val aliceStudentId = Regex("\"studentId\":\"(lti-[a-f0-9]+)\"").find(students)!!.groupValues[1]

        val reset = staffClient.post("/api/staff/students/$aliceStudentId/reset-session")
        assertEquals(HttpStatusCode.OK, reset.status)

        val afterReset = studentClient.get("/api/sources")
        assertEquals(HttpStatusCode.Unauthorized, afterReset.status, "the old session must be rejected after a staff reset")

        // Re-launching (the same recovery path ADR 0006 establishes) restores access.
        studentClient.loginViaLti(subject = "alice", roles = listOf(LEARNER_ROLE))
        assertEquals(HttpStatusCode.OK, studentClient.get("/api/sources").status)
    }

    @Test
    fun `reset-session rejects a malformed studentId without touching the directory`() = testApplication {
        val factory = newFactory()
        application { installModule(factory) }
        val staffClient = createClient { install(HttpCookies) }
        staffClient.loginViaLti(subject = "prof-jones", roles = listOf(INSTRUCTOR_ROLE))

        // A single path segment that still fails studentIdPattern (spaces/semicolons aren't in
        // [A-Za-z0-9_-]) — a real `../` traversal attempt wouldn't even match the {id} route.
        val response = staffClient.post("/api/staff/students/not%20a%20valid%20id;/reset-session")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
